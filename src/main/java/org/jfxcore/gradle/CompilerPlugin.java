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
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.process.CommandLineArgumentProvider;
import org.jfxcore.gradle.tasks.FxmlSourceInfo;
import org.jfxcore.gradle.tasks.ProcessFxmlTask;
import org.jfxcore.gradle.tasks.RunCompilerAction;
import org.jfxcore.markup.embed.Markup;
import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CompilerPlugin implements Plugin<Project> {

    private static final String MARKUP_ANNOTATION_PROCESSOR = "org.jfxcore.compiler.MarkupProcessor";
    private static final String KOTLIN_PLUGIN_ID = "org.jetbrains.kotlin.jvm";
    private static final String KSP_PLUGIN_ID = "com.google.devtools.ksp";

    @Override
    public void apply(Project project) {
        // For Kotlin projects, consumers must apply KSP so the symbol processor can be wired in.
        project.getPluginManager().withPlugin(KOTLIN_PLUGIN_ID, plugin ->
            project.afterEvaluate(ignored -> {
                if (!project.getPluginManager().hasPlugin(KSP_PLUGIN_ID)) {
                    project.getLogger().warn("""
                        NOTE: The org.jfxcore.fxmlplugin was applied to {},
                              but the Kotlin Symbol Processing plugin ({}) was not found.
                              Apply the KSP plugin to enable @{}-based embedded markup processing.
                        """, project.getDisplayName(), KSP_PLUGIN_ID, Markup.class.getSimpleName());
                }
            }));

        List<File> compilerClasspathEntries = getCompilerClasspathEntries();
        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        sourceSets.configureEach(sourceSet -> configureTasksForSourceSet(project, sourceSet, compilerClasspathEntries));
    }

    private void configureTasksForSourceSet(Project project, SourceSet sourceSet, List<File> compilerClasspathEntries) {
        ConfigurableFileCollection processorSearchPath = project.getObjects().fileCollection();
        processorSearchPath.from(sourceSet.getCompileClasspath());

        ConfigurableFileCollection postCompileSearchPath = project.getObjects().fileCollection();
        postCompileSearchPath.from(sourceSet.getCompileClasspath());
        postCompileSearchPath.from(sourceSet.getOutput());

        FileCollection srcDirs = project.files(sourceSet.getAllSource().getSrcDirs());
        File classesDir = sourceSet.getJava().getClassesDirectory().get().getAsFile();
        File genSrcDir = PathHelper.getGeneratedSourcesDir(project, sourceSet);
        Map<File, List<File>> fxmlFiles = PathHelper.getFxmlFilesPerSourceDirectory(srcDirs.getFiles(), genSrcDir);

        Provider<ProcessFxmlTask> processFxmlTask = project.getTasks().register(
            sourceSet.getTaskName(ProcessFxmlTask.VERB, ProcessFxmlTask.TARGET),
            ProcessFxmlTask.class, task -> {
                task.getSearchPath().set(processorSearchPath);
                task.getCompileClasspath().set(sourceSet.getCompileClasspath());
                task.getFxmlSourceInfo().set(fxmlFiles.entrySet().stream()
                    .map(entry -> {
                        FxmlSourceInfo sourceInfo = project.getObjects().newInstance(FxmlSourceInfo.class);
                        sourceInfo.getSourceDir().set(entry.getKey());
                        sourceInfo.getFxmlFiles().set(project.files(entry.getValue()));
                        return sourceInfo;
                    }).toList());
                task.getClassesDir().set(classesDir);
                task.getGeneratedSourcesDir().set(genSrcDir);
                task.getIntermediateBuildDir().convention(
                    project.getLayout()
                           .getBuildDirectory()
                           .dir("fxml/" + sourceSet.getName()));
            });

        // For each source set, add the corresponding generated sources directory, so it can be
        // picked up by the Java compiler.
        sourceSet.getJava().srcDir(processFxmlTask.flatMap(ProcessFxmlTask::getGeneratedSourcesDir));

        // The intermediate directory is used by the FXML compiler to store compilation unit descriptors.
        Provider<Directory> intermediateBuildDir = processFxmlTask.flatMap(ProcessFxmlTask::getIntermediateBuildDir);

        project.getTasks().named(sourceSet.getCompileJavaTaskName(), JavaCompile.class, task -> {
            CompileOptions options = task.getOptions();

            // Several options need to be specified as Java compiler arguments, as they are required
            // when embedded FXML documents are processed by the markup annotation processor.
            options.getCompilerArgs().addAll(List.of("-processor", MARKUP_ANNOTATION_PROCESSOR));
            options.getCompilerArgumentProviders().add(new CompilerArgumentsProvider(
                CompilerArgumentsProvider.Target.JAVA, project.getObjects(),
                srcDirs, processorSearchPath, intermediateBuildDir));

            // Run the FXML compiler at the end of compileJava's action list. This is important for
            // incremental compilation: Gradle will fingerprint the compiled class files after the
            // last task action is executed, i.e. after the FXML compiler has rewritten the bytecode.
            task.doLast(
                project.getObjects().newInstance(
                    RunCompilerAction.class,
                    postCompileSearchPath, intermediateBuildDir.get().getAsFile(),
                    classesDir, project.getLogger()));
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
        project.getPluginManager().withPlugin("java", plugin ->
            addFilesDependency(project, sourceSet.getAnnotationProcessorConfigurationName(), compilerClasspathEntries));

        project.getPluginManager().withPlugin(KSP_PLUGIN_ID, plugin -> {
            String sourceSetName = sourceSet.getName();
            String kspTaskName = sourceSet.getTaskName("ksp", "Kotlin");
            String kspConfigurationName = sourceSetName.equals(SourceSet.MAIN_SOURCE_SET_NAME)
                ? "ksp" : "ksp" + Character.toUpperCase(sourceSetName.charAt(0)) + sourceSetName.substring(1);

            addFilesDependency(project, kspConfigurationName, compilerClasspathEntries);

            project.getTasks().configureEach(task -> {
                if (task.getName().equals(kspTaskName)) {
                    task.dependsOn(processFxmlTask);
                    addCommandLineArgumentProvider(task, new CompilerArgumentsProvider(
                        CompilerArgumentsProvider.Target.KOTLIN, project.getObjects(),
                        srcDirs, processorSearchPath, intermediateBuildDir));
                }
            });
        });
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

    private static void addFilesDependency(Project project, String configurationName, List<File> entries) {
        if (project.getConfigurations().findByName(configurationName) != null) {
            project.getDependencies().add(configurationName, project.files(entries));
        }
    }

    private static void addFileUrl(Set<File> entries, URL url) {
        if (url == null || !url.getProtocol().equals("file")) {
            return;
        }

        try {
            entries.add(Path.of(url.toURI()).toFile());
        } catch (URISyntaxException ex) {
            throw new RuntimeException("Failed to resolve classpath entry: " + url, ex);
        }
    }

    private static List<File> getCompilerClasspathEntries() {
        Set<File> entries = new LinkedHashSet<>();
        addFileUrl(entries, Markup.class.getProtectionDomain().getCodeSource().getLocation());
        return List.copyOf(entries);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void sneakyThrow(Throwable ex) throws T {
        throw (T)ex;
    }
}
