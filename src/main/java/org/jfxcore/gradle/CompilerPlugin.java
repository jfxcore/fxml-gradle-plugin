// Copyright (c) 2023, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.api.tasks.compile.JavaCompile;
import org.jfxcore.gradle.tasks.RunCompilerAction;
import org.jfxcore.gradle.tasks.FxmlSourceInfo;
import org.jfxcore.gradle.tasks.ProcessFxmlTask;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

public class CompilerPlugin implements Plugin<Project> {

    private static final String MARKUP_ANNOTATION_PROCESSOR = "org.jfxcore.markup.processor.MarkupProcessor";
    private static final String INTERMEDIATE_BUILD_DIR_OPT = "org.jfxcore.markup.processor.intermediateBuildDir";
    private static final String SOURCE_DIRS_OPT = "org.jfxcore.markup.processor.sourceDirs";
    private static final String SEARCH_PATH_OPT = "org.jfxcore.markup.processor.searchPath";

    @Override
    public void apply(Project project) {
        // Add the FXML compiler as a compile-only dependency of the project to enable markup annotation processing.
        project.getPluginManager().withPlugin("java", plugin -> {
            try (var stream = CompilerPlugin.class.getClassLoader().getResourceAsStream("plugin.properties")) {
                var props = new Properties();
                props.load(stream);

                String dependency = "org.jfxcore:fxml-compiler:" + Objects.requireNonNull(
                    props.getProperty("compiler-version"),
                    "compiler-version not specified in plugin.properties");

                project.getDependencies().add("compileOnly", dependency);
                project.getDependencies().add("annotationProcessor", dependency);
            } catch (IOException ex) {
                throw new RuntimeException("Failed to load plugin properties", ex);
            }
        });

        // For each source set, add the corresponding generated sources directory, so it can be
        // picked up by the Java compiler.
        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);

        sourceSets.configureEach(sourceSet ->
            sourceSet.getJava().srcDir(PathHelper.getGeneratedSourcesDir(project, sourceSet)));

        sourceSets.configureEach(sourceSet -> configureTasksForSourceSet(project, sourceSet));
    }

    private void configureTasksForSourceSet(Project project, SourceSet sourceSet) {
        ConfigurableFileCollection searchPath = project.getObjects().fileCollection();
        searchPath.from(sourceSet.getOutput());
        searchPath.from(sourceSet.getCompileClasspath());

        FileCollection srcDirs = project.files(sourceSet.getAllSource().getSrcDirs());
        File classesDir = sourceSet.getJava().getClassesDirectory().get().getAsFile();
        File genSrcDir = PathHelper.getGeneratedSourcesDir(project, sourceSet);
        Map<File, List<File>> fxmlFiles = PathHelper.getFxmlFilesPerSourceDirectory(srcDirs.getFiles(), genSrcDir);

        Provider<ProcessFxmlTask> processFxmlTask = project.getTasks().register(
            sourceSet.getTaskName(ProcessFxmlTask.VERB, ProcessFxmlTask.TARGET),
            ProcessFxmlTask.class, task -> {
                task.getSearchPath().set(searchPath);
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

        Provider<Directory> intermediateBuildDir = processFxmlTask.flatMap(ProcessFxmlTask::getIntermediateBuildDir);

        project.getTasks().named(sourceSet.getCompileJavaTaskName(), JavaCompile.class, task -> {
            CompileOptions options = task.getOptions();

            // Several options need to be specified as Java compiler arguments, as they are required
            // when embedded FXML documents are processed by the markup annotation processor.
            options.getCompilerArgumentProviders().add(() -> List.of(
                "-processor", MARKUP_ANNOTATION_PROCESSOR,
                "-A" + SOURCE_DIRS_OPT + "=" + srcDirs.getAsPath(),
                "-A" + SEARCH_PATH_OPT + "=" + searchPath.getAsPath(),
                "-A" + INTERMEDIATE_BUILD_DIR_OPT + "=" + intermediateBuildDir.get().getAsFile().getAbsolutePath()
            ));

            // Run the FXML compiler at the end of compileJava's action list. This is important for
            // incremental compilation: Gradle will fingerprint the compiled class files after the
            // last task action is executed, i.e. after the FXML compiler has rewritten the bytecode.
            task.doLast(
                project.getObjects().newInstance(
                    RunCompilerAction.class,
                    searchPath, intermediateBuildDir.get().getAsFile(),
                    classesDir, project.getLogger()));
        });

        for (String target : new String[] { "java", "kotlin", "scala", "groovy" }) {
            String compileTaskName = sourceSet.getTaskName("compile", target);
            Task compileTask = project.getTasks().findByName(compileTaskName);
            if (compileTask != null) {
                compileTask.dependsOn(processFxmlTask);
            }
        }
    }
}
