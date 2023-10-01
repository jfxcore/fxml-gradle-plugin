// Copyright (c) 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.gradle;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskCollection;
import org.gradle.jvm.tasks.Jar;
import org.jfxcore.gradle.compiler.Compiler;
import org.jfxcore.gradle.compiler.CompilerService;
import org.jfxcore.gradle.tasks.ProcessMarkupTask;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
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
        List<TaskCollection<Jar>> dependentJarTasks =  project.getConfigurations().stream()
            .flatMap(dep -> dep.getDependencies().stream())
            .filter(dep -> dep instanceof ProjectDependency)
            .map(dep -> (ProjectDependency)dep)
            .map(ProjectDependency::getDependencyProject)
            .distinct()
            .map(dp -> dp.getTasks().withType(Jar.class).matching(jar -> jar.getName().equals("jar")))
            .toList();

        for (SourceSet sourceSet : new PathHelper(project).getSourceSets()) {
            configureTasksForSourceSet(project, sourceSet, dependentJarTasks);
        }
    }

    private void configureTasksForSourceSet(
            Project project, SourceSet sourceSet, List<TaskCollection<Jar>> dependentJarTasks) {
        ProcessMarkupTask processMarkupTask = project.getTasks().create(
            sourceSet.getTaskName(ProcessMarkupTask.VERB, ProcessMarkupTask.TARGET),
            ProcessMarkupTask.class, task -> {
                task.getSourceSet().set(sourceSet);
                dependentJarTasks.forEach(task::dependsOn);
            });

        // Run the FXML compiler at the end of compileJava's action list. This is important for
        // incremental compilation: Gradle will fingerprint the compiled class files after the
        // last task action is executed, i.e. after the FXML compiler has rewritten the bytecode.
        getTask(project, sourceSet.getCompileJavaTaskName()).doLast(task -> runCompiler(project, sourceSet));

        for (String target : new String[] { "java", "kotlin", "scala", "groovy" }) {
            String compileTaskName = sourceSet.getTaskName("compile", target);
            Task compileTask = project.getTasks().findByName(compileTaskName);
            if (compileTask != null) {
                compileTask.dependsOn(processMarkupTask);
            }
        }
    }

    private void runCompiler(Project project, SourceSet sourceSet) {
        Compiler compiler = null;

        try {
            PathHelper pathHelper = new PathHelper(project);
            compiler = CompilerService.get(project).getCompiler(sourceSet);

            if (compiler != null) {
                // If we have a compiler at this point, then ProcessMarkupTask has run before.
                // This means that all of our FXML class files are uncompiled, and need to be
                // compiled by the FXML compiler.
                compiler.compileFiles();
            } else {
                // If we don't have a compiler, ProcessMarkupTask was skipped. We can't be sure
                // that compileJava didn't re-compile our FXML class files, which would undo
                // the modifications that the FXML compiler has made to the files.
                // Luckily, we can detect whether a class file was compiled by the FXML compiler
                // since it includes a custom class file attribute. We invoke the compiler to
                // give us a list of all FXML class files that don't include the custom attribute,
                // and recompile only those files.
                File genSrcDir = pathHelper.getGeneratedSourcesDir(sourceSet);
                var markupFilesPerSourceDirectory = pathHelper.getMarkupFilesPerSourceDirectory(sourceSet);
                var recompilableMarkupFilesPerSourceDirectory = new HashMap<File, List<File>>();

                compiler = CompilerService.get(project).newCompiler(project, sourceSet, genSrcDir);
                compiler.addFiles(markupFilesPerSourceDirectory);

                for (var entry : compiler.getCompilationUnits().entrySet()) {
                    for (var compilationUnit :  entry.getValue()) {
                        if (compilationUnit.markupClassFile().exists()
                                && !compiler.isCompiledFile(compilationUnit.markupClassFile())) {
                            recompilableMarkupFilesPerSourceDirectory
                                .computeIfAbsent(entry.getKey(), key -> new ArrayList<>())
                                .add(compilationUnit.markupFile());
                        }
                    }
                }

                if (recompilableMarkupFilesPerSourceDirectory.size() > 0) {
                    compiler = CompilerService.get(project).newCompiler(project, sourceSet, genSrcDir);
                    compiler.addFiles(recompilableMarkupFilesPerSourceDirectory);
                    compiler.processFiles();
                    compiler.compileFiles();
                }
            }
        } catch (Throwable ex) {
            // If the FXML compiler fails, we need to delete all generated files.
            // This ensures that ProcessMarkupTask is no longer up-to-date, and it will
            // regenerate the files on the next build, causing the FXML compiler to run
            // once again.
            List<File> generatedFiles = compiler != null ?
                compiler.getCompilationUnits().getAllGeneratedFiles() : List.of();

            for (File file : generatedFiles) {
                if (file.exists()) {
                    try {
                        Files.delete(file.toPath());
                    } catch (IOException ex2) {
                        ex2.addSuppressed(ex);
                        throw new GradleException("Cannot delete " + file, ex2);
                    }
                }
            }

            if (compiler != null) {
                compiler.getExceptionHelper().handleException(ex, project.getLogger());
            }

            throw new GradleException("Internal compiler error", ex);
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
