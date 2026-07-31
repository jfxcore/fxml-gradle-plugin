// Copyright (c) 2023, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
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
import java.util.List;
import java.util.function.Supplier;

public final class CompilerPlugin implements Plugin<Project> {

    static final String MARKUP_ANNOTATION_PROCESSOR = "org.jfxcore.compiler.MarkupProcessor";

    private static final String KOTLIN_PLUGIN_ID = "org.jetbrains.kotlin.jvm";
    private static final String KSP_PLUGIN_ID = "com.google.devtools.ksp";
    private static final String FXML_EXTENSION = ".fxml";

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

        project.getExtensions()
            .getByType(SourceSetContainer.class)
            .configureEach(sourceSet -> configureTasksForSourceSet(
                project, sourceSet, extension.getAnnotationProcessing(), extension.getSourceFileExtensions()));
    }

    private void configureTasksForSourceSet(Project project,
                                            SourceSet sourceSet,
                                            Provider<Boolean> annotationProcessing,
                                            Provider<List<String>> sourceFileExtensions) {
        ConfigurableFileCollection processorSearchPath = project.getObjects().fileCollection();
        processorSearchPath.from(sourceSet.getCompileClasspath());

        ConfigurableFileCollection postCompileSearchPath = project.getObjects().fileCollection();
        postCompileSearchPath.from(sourceSet.getCompileClasspath());
        postCompileSearchPath.from(sourceSet.getOutput());

        FileCollection srcDirs = project.files(sourceSet.getAllSource().getSrcDirs());
        File classesDir = sourceSet.getJava().getClassesDirectory().get().getAsFile();
        File genSrcDir = PathHelper.getGeneratedSourcesDir(project, sourceSet);

        // The intermediate directories are used by the FXML compiler to store compilation unit descriptors.
        Provider<Directory> intermediateBuildDir = getIntermediateBuildDir(project, sourceSet, "default");
        Provider<Directory> embeddedIntermediateBuildDir = getIntermediateBuildDir(project, sourceSet, "annotationProcessor");
        Provider<Directory> embeddedKotlinIntermediateBuildDir = getIntermediateBuildDir(project, sourceSet, "ksp");

        Provider<ProcessFxmlTask> processFxmlTask = project.getTasks().register(
            sourceSet.getTaskName(ProcessFxmlTask.VERB, ProcessFxmlTask.TARGET),
            ProcessFxmlTask.class, task -> {
                task.getSearchPath().set(processorSearchPath);
                task.getCompileClasspath().set(sourceSet.getCompileClasspath());

                // Keep the task inputs live so additions and renames are visible when the configuration cache is
                // reused. Enumerating the directory here would freeze the set of file paths in the cached model.
                PatternSet fxmlFilePatterns = new PatternSet();
                fxmlFilePatterns.setCaseSensitive(false);

                List<String> includes = sourceFileExtensions.get().stream()
                    .map(extension -> extension.startsWith(".") ? extension : "." + extension)
                    .map(extension -> "**/*" + extension)
                    .toList();

                if (includes.isEmpty()) {
                    fxmlFilePatterns.exclude("**/*");
                } else {
                    fxmlFilePatterns.include(includes);
                }

                task.getFxmlSourceInfo().set(srcDirs.getFiles().stream()
                    .filter(sourceDir -> !genSrcDir.equals(sourceDir))
                    .map(sourceDir -> {
                        FxmlSourceInfo sourceInfo = project.getObjects().newInstance(FxmlSourceInfo.class);
                        sourceInfo.getSourceDir().set(sourceDir);
                        sourceInfo.getFxmlFiles().set(project.fileTree(sourceDir).matching(fxmlFilePatterns));
                        return sourceInfo;
                    }).toList());
                task.getClassesDir().set(classesDir);
                task.getGeneratedSourcesDir().set(genSrcDir);
                task.getIntermediateBuildDir().convention(intermediateBuildDir);
            });

        // For each source set, add the corresponding generated sources directory, so it can be
        // picked up by the Java compiler.
        sourceSet.getJava().srcDir(processFxmlTask.flatMap(ProcessFxmlTask::getGeneratedSourcesDir));

        project.getTasks().named(sourceSet.getCompileJavaTaskName(), JavaCompile.class, task -> {
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

            project.getTasks().configureEach(task -> {
                if (task.getName().equals(kspTaskName)) {
                    task.dependsOn(processFxmlTask);

                    addCommandLineArgumentProvider(task, new CompilerArgumentsProvider(
                        CompilerArgumentsProvider.Target.KOTLIN,
                        project.getObjects(), annotationProcessing,
                        srcDirs, processorSearchPath, embeddedKotlinIntermediateBuildDir));

                    project.getTasks().named(sourceSet.getCompileJavaTaskName(), JavaCompile.class)
                           .configure(compileJava -> compileJava.mustRunAfter(task));
                }
            });
        });
    }

    private Provider<Directory> getIntermediateBuildDir(Project project, SourceSet sourceSet, String name) {
        return project.getLayout().getBuildDirectory().dir("fxml/" + name + "/" + sourceSet.getName());
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
