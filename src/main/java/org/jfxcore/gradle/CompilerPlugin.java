// Copyright (c) 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.gradle;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.tasks.SourceSet;
import org.jfxcore.gradle.compiler.Compiler;
import org.jfxcore.gradle.compiler.CompilerService;
import org.jfxcore.gradle.compiler.ExceptionHelper;
import org.jfxcore.gradle.tasks.ProcessMarkupTask;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class CompilerPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        // For each source set, add the corresponding generated sources directory, so it can be
        // picked up by the Java compiler.
        var pathHelper = new PathHelper(project);
        for (SourceSet sourceSet : pathHelper.getSourceSets()) {
            sourceSet.getJava().srcDir(pathHelper.getGeneratedSourcesDir(sourceSet));
        }

        CompilerService.register(project);
        project.afterEvaluate(this::configureTasks);
    }

    private void configureTasks(Project project) {
        List<Task> dependentJarTasks =  project.getConfigurations().stream()
            .flatMap(dep -> dep.getDependencies().stream())
            .filter(dep -> dep instanceof ProjectDependency)
            .map(dep -> (ProjectDependency)dep)
            .map(ProjectDependency::getDependencyProject)
            .distinct()
            .flatMap(dp -> dp.getTasksByName("jar", false).stream())
            .toList();

        for (SourceSet sourceSet : new PathHelper(project).getSourceSets()) {
            configureTasksForSourceSet(project, sourceSet, dependentJarTasks);
        }
    }

    private void configureTasksForSourceSet(
            Project project, SourceSet sourceSet, List<Task> dependentJarTasks) {
        ProcessMarkupTask processMarkupTask = project.getTasks().create(
            sourceSet.getTaskName(ProcessMarkupTask.VERB, ProcessMarkupTask.TARGET),
            ProcessMarkupTask.class, task -> {
                task.getSourceSet().set(sourceSet);
                dependentJarTasks.forEach(task::dependsOn);
            });

        // Run the FXML compiler at the end of compileJava's action list. This is important for
        // incremental compilation: Gradle will fingerprint the compiled class files after the
        // last task action is executed, i.e. after the FXML compiler has rewritten the bytecode.
        getTask(project, sourceSet.getCompileJavaTaskName()).doLast(task ->
            ExceptionHelper.run(
                project,
                CompilerService.get(project).getCompiler(sourceSet),
                compiler -> runCompiler(project, sourceSet, compiler)));

        for (String target : new String[] { "java", "kotlin", "scala", "groovy" }) {
            String compileTaskName = sourceSet.getTaskName("compile", target);
            Task compileTask = project.getTasks().findByName(compileTaskName);
            if (compileTask != null) {
                compileTask.dependsOn(processMarkupTask);
            }
        }
    }

    private void runCompiler(Project project, SourceSet sourceSet, Compiler compiler) throws Throwable {
        try {
            PathHelper pathHelper = new PathHelper(project);

            if (compiler != null) {
                // We will only have a compiler if ProcessMarkupTask has run. In this case,
                // we need to invoke the compiler and back up the modified class files to
                // the temp directory.
                compiler.compileFiles();
                pathHelper.copyClassFilesToTempDirectory(sourceSet, compiler.getGeneratedJavaFiles());
            } else {
                // If we don't have a compiler, ProcessMarkupTask was skipped. We can't be
                // sure that compileJava didn't re-compile our generated Java files, which
                // would undo the modifications that the FXML compiler has made to the files.
                // In this case, we need to restore the backup class files from the temp directory.
                compiler = CompilerService.get(project).newCompiler(
                    sourceSet, pathHelper.getGeneratedSourcesDir(sourceSet), project.getLogger());
                compiler.addFiles(pathHelper.getMarkupFilesPerSourceDirectory(sourceSet));
                pathHelper.restoreClassFilesFromTempDirectory(sourceSet, compiler.getGeneratedJavaFiles());
            }
        } catch (Throwable ex) {
            // If the FXML compiler fails, we need to delete all generated Java files.
            // This ensures that ProcessMarkupTask is no longer up-to-date, and it will
            // regenerate the files on the next build, causing the FXML compiler to run
            // once again.
            for (File file : (compiler != null ? compiler.getGeneratedJavaFiles() : List.<File>of())) {
                Path filePath = file.toPath();
                if (Files.exists(filePath)) {
                    try {
                        Files.delete(filePath);
                    } catch (IOException ex2) {
                        ex2.addSuppressed(ex);
                        throw new GradleException("Cannot delete " + filePath, ex2);
                    }
                }
            }

            throw ex;
        } finally {
            if (compiler != null) {
                compiler.close();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends Task> T getTask(Project project, String name) {
        return (T)project.getTasksByName(name, false).stream()
            .findFirst()
            .orElseThrow(() -> new GradleException("Task not found: " + name));
    }

}
