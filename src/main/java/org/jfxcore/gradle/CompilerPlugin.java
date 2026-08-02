// Copyright (c) 2023, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.gradle;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.process.CommandLineArgumentProvider;
import org.jfxcore.gradle.tasks.FxmlSourceInfo;
import org.jfxcore.gradle.tasks.ProcessFxmlTask;
import org.jfxcore.gradle.tasks.RunCompilerAction;
import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public final class CompilerPlugin implements Plugin<Project> {

    static final String MARKUP_ANNOTATION_PROCESSOR = "org.jfxcore.compiler.MarkupProcessor";

    private static final String KOTLIN_PLUGIN_ID = "org.jetbrains.kotlin.jvm";
    private static final String KSP_PLUGIN_ID = "com.google.devtools.ksp";
    private static final String FXML_EXTENSION = "fxml";

    @Override
    public void apply(Project project) {
        var extension = project.getExtensions().create(CompilerPluginExtension.NAME, CompilerPluginExtension.class);
        extension.getAnnotationProcessing().convention(false);
        extension.getSourceFileExtensions().convention(List.of(FXML_EXTENSION));

        // For Kotlin projects that have enabled annotation processing, consumers must apply the
        // Kotlin Symbol Processing plugin so the symbol processor can be wired in.
        project.getPluginManager().withPlugin(KOTLIN_PLUGIN_ID, plugin ->
            project.afterEvaluate(ignored -> {
                boolean annotationProcessing = extension.getAnnotationProcessing().getOrElse(false);
                boolean hasKspPlugin = project.getPluginManager().hasPlugin(KSP_PLUGIN_ID);

                if (annotationProcessing && !hasKspPlugin) {
                    project.getLogger().warn("""
                        WARNING: org.jfxcore.fxmlplugin is configured to use annotation processing in {},
                                 but the Kotlin Symbol Processing plugin ({}) was not found.
                                 Apply the KSP plugin to support annotation processing, or disable annotation processing
                                 in the configuration block of org.jfxcore.fxmlplugin to prevent this warning.
                        """, project.getDisplayName(), KSP_PLUGIN_ID);
                }
            }));

        var javaPluginApplied = new AtomicBoolean();

        // Plugin blocks are ordered, and users should not need to put this plugin after the Java plugin.
        // Wait for Java support instead of looking up SourceSetContainer eagerly.
        project.getPluginManager().withPlugin("java", ignored -> {
            javaPluginApplied.set(true);

            project.getExtensions()
                .getByType(SourceSetContainer.class)
                .configureEach(sourceSet -> configureTasksForSourceSet(
                    project, sourceSet,
                    extension.getAnnotationProcessing(),
                    extension.getSourceFileExtensions()));
        });

        project.afterEvaluate(ignored -> {
            if (!javaPluginApplied.get()) {
                throw new GradleException(
                    "org.jfxcore.fxmlplugin requires the Java plugin to be applied for "
                    + project.getDisplayName());
            }
        });
    }

    private void configureTasksForSourceSet(Project project,
                                            SourceSet sourceSet,
                                            Provider<Boolean> annotationProcessing,
                                            Provider<List<String>> sourceFileExtensions) {
        Provider<Directory> generatedSourcesDir = PathHelper.getGeneratedSourcesDirectory(project, sourceSet);

        // A Gradle file collection can carry task dependencies in addition to file paths. Later, this plugin adds
        // generatedSourcesDir to the Java source set and marks it as an output of processFxml. Using getAllSource()
        // directly as an input of processFxml could therefore make Gradle infer that processFxml depends on itself:
        //     processFxml -> allSource -> generatedSourcesDir -> processFxml
        //
        // To fix this, we read the source directories lazily so directories added by later configuration are
        // included, convert them to plain File values to discard task dependency metadata, and exclude this
        // task's own output.
        FileCollection srcDirs = project.files(project.provider(() -> {
            File generatedSources = generatedSourcesDir.get().getAsFile();
            ArrayList<File> result = new ArrayList<>(sourceSet.getAllSource().getSrcDirs());
            result.remove(generatedSources);
            return result;
        }));

        Provider<FileCollection> compileClasspath = project.provider(sourceSet::getCompileClasspath);
        ConfigurableFileCollection processorSearchPath = project.getObjects().fileCollection();
        processorSearchPath.from(compileClasspath);

        ConfigurableFileCollection postCompileSearchPath = project.getObjects().fileCollection();
        postCompileSearchPath.from(compileClasspath);
        postCompileSearchPath.from(sourceSet.getOutput());

        Provider<Directory> classesDir = project.getLayout().dir(project.provider(() ->
            project.getTasks()
                .named(sourceSet.getCompileJavaTaskName(), JavaCompile.class)
                .get().getDestinationDirectory().get().getAsFile()));

        // The intermediate directories are used by the FXML compiler to store compilation unit descriptors.
        Provider<Directory> intermediateBuildDir = getIntermediateBuildDir(project, sourceSet, "default");
        Provider<Directory> embeddedIntermediateBuildDir = getIntermediateBuildDir(project, sourceSet, "annotationProcessor");
        Provider<Directory> embeddedKotlinIntermediateBuildDir = getIntermediateBuildDir(project, sourceSet, "ksp");

        Provider<ProcessFxmlTask> processFxmlTask = project.getTasks().register(
            sourceSet.getTaskName(ProcessFxmlTask.VERB, ProcessFxmlTask.TARGET),
            ProcessFxmlTask.class, task -> {
                task.getSearchPath().set(processorSearchPath);
                task.getCompileClasspath().set(compileClasspath);
                task.getClassesDir().set(classesDir);
                task.getGeneratedSourcesDir().set(generatedSourcesDir);
                task.getIntermediateBuildDir().convention(intermediateBuildDir);

                // Keep the task inputs live so additions and renames are visible when the configuration cache is
                // reused. Enumerating the directory here would freeze the set of file paths in the cached model.
                task.getFxmlSourceInfo().set(
                    project.provider(() -> srcDirs.getFiles().stream()
                        .map(sourceDir -> createSourceInfo(project, sourceDir, sourceFileExtensions))
                        .toList()));
            });

        // For each source set, add the corresponding generated sources directory, so it can be
        // picked up by the Java compiler.
        sourceSet.getJava().srcDir(processFxmlTask.flatMap(ProcessFxmlTask::getGeneratedSourcesDir));

        project.getTasks().named(sourceSet.getCompileJavaTaskName(), JavaCompile.class, task -> {
            // The generated Java stub can remain byte-for-byte identical even though the FXML file was changed.
            // Include the descriptor contents in JavaCompile's identity so Gradle cannot restore bytecode rewritten
            // for an older descriptor from the build cache and skip this task's post-compile action.
            task.getInputs().dir(intermediateBuildDir)
                .withPropertyName("fxmlDescriptors")
                .withPathSensitivity(PathSensitivity.RELATIVE);

            // Several options need to be specified as Java compiler arguments, as they are required
            // when embedded FXML documents are processed by the markup annotation processor.
            task.getOptions().getCompilerArgumentProviders().add(new CompilerArgumentsProvider(
                CompilerArgumentsProvider.Target.JAVA,
                project.getObjects(), annotationProcessing,
                srcDirs, processorSearchPath, embeddedIntermediateBuildDir));

            // Run the FXML compiler at the end of compileJava's action list. This is important for
            // incremental compilation: Gradle will fingerprint the compiled class files after the
            // last task action is executed, i.e. after the FXML compiler has rewritten the bytecode.
            task.doLast(
                project.getObjects().newInstance(
                    RunCompilerAction.class, postCompileSearchPath,
                    List.of(intermediateBuildDir, embeddedIntermediateBuildDir, embeddedKotlinIntermediateBuildDir),
                    classesDir));
        });

        for (String target : new String[] {"Java", "Kotlin", "Scala", "Groovy"}) {
            String compileTaskName = sourceSet.getTaskName("compile", target);
            project.getTasks().configureEach(task -> {
                if (task.getName().equals(compileTaskName)) {
                    task.dependsOn(processFxmlTask);
                }
            });
        }

        // Add the FXML compiler as an annotationProcessor dependency of the project
        // to enable markup annotation processing.
        addConditionalDependency(
            project, annotationProcessing,
            sourceSet.getAnnotationProcessorConfigurationName(),
            CompilerPlugin::getCompilerJar);

        // In Kotlin projects, we need to add the compiler to the ksp{SourceSet} configuration.
        project.getPluginManager().withPlugin(KSP_PLUGIN_ID, plugin -> {
            String kspTaskName = sourceSet.getTaskName("ksp", "Kotlin");
            String kspConfigurationName = sourceSet.getTaskName("ksp", "");
            addConditionalDependency(project, annotationProcessing, kspConfigurationName, CompilerPlugin::getCompilerJar);

            project.getTasks().named(sourceSet.getCompileJavaTaskName(), JavaCompile.class)
                .configure(compileJava -> {
                    compileJava.mustRunAfter(kspTaskName);
                    compileJava.getInputs().dir(embeddedKotlinIntermediateBuildDir)
                        .withPropertyName("kspFxmlDescriptors")
                        .withPathSensitivity(PathSensitivity.RELATIVE);
                });

            project.getTasks().configureEach(task -> {
                if (task.getName().equals(kspTaskName)) {
                    task.dependsOn(processFxmlTask);

                    addCommandLineArgumentProvider(task, new CompilerArgumentsProvider(
                        CompilerArgumentsProvider.Target.KOTLIN,
                        project.getObjects(), annotationProcessing,
                        srcDirs, processorSearchPath, embeddedKotlinIntermediateBuildDir));
                }
            });
        });
    }

    private Provider<Directory> getIntermediateBuildDir(Project project, SourceSet sourceSet, String name) {
        return project.getLayout().getBuildDirectory().dir("fxml/" + name + "/" + sourceSet.getName());
    }

    private static FxmlSourceInfo createSourceInfo(
            Project project, File sourceDir, Provider<List<String>> sourceFileExtensions) {
        PatternSet patterns = new PatternSet();
        patterns.include(element ->
            element.isDirectory() || matchesExtension(
                element.getName(), sourceFileExtensions.getOrElse(List.of())));

        // FileTree.getElements() deliberately flattens the tree. Gradle then tracks only matching FXML files,
        // without inferring dependencies from unrelated generated files beneath a source root.
        FileTree fxmlFileTree = project.fileTree(sourceDir).matching(patterns);
        FileCollection fxmlFiles = project.files(fxmlFileTree.getElements());
        FxmlSourceInfo sourceInfo = project.getObjects().newInstance(FxmlSourceInfo.class);
        sourceInfo.getFxmlFiles().set(fxmlFiles);
        sourceInfo.getSourceDir().set(sourceDir);
        return sourceInfo;
    }

    private static boolean matchesExtension(String fileName, List<String> extensions) {
        String normalizedFileName = fileName.toLowerCase(Locale.ROOT);

        return extensions.stream()
            .map(String::trim)
            .filter(extension -> !extension.isEmpty())
            .map(extension -> extension.startsWith(".") ? extension : "." + extension)
            .map(extension -> extension.toLowerCase(Locale.ROOT))
            .anyMatch(normalizedFileName::endsWith);
    }

    private static File getCompilerJar() {
        try {
            URL url = Class.forName(MARKUP_ANNOTATION_PROCESSOR).getProtectionDomain().getCodeSource().getLocation();
            return Path.of(url.toURI()).toFile();
        } catch (ClassNotFoundException | URISyntaxException ex) {
            throw new RuntimeException("Failed to locate the FXML compiler JAR file", ex);
        }
    }

    private static void addConditionalDependency(Project project,
                                                 Provider<Boolean> condition,
                                                 String configurationName,
                                                 Supplier<File> file) {
        project.getConfigurations()
            .getByName(configurationName)
            .getDependencies()
            .addAllLater(
                condition.map(enabled -> enabled
                    ? List.of(project.getDependencies().create(project.files(file.get())))
                    : List.of())
            );
    }

    private static void addCommandLineArgumentProvider(Object task, CommandLineArgumentProvider provider) {
        try {
            Object providers = task.getClass()
                .getMethod("getCommandLineArgumentProviders")
                .invoke(task);

            providers.getClass()
                .getMethod("add", Object.class)
                .invoke(providers, provider);
        } catch (ReflectiveOperationException ex) {
            sneakyThrow(ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void sneakyThrow(Throwable ex) throws T {
        throw (T)ex;
    }
}
