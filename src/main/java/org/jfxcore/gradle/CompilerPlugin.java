// Copyright (c) 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.gradle;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.tasks.SourceSet;
import org.jfxcore.gradle.compiler.CompilerService;
import org.jfxcore.gradle.compiler.ExceptionHelper;
import org.jfxcore.gradle.tasks.CompileMarkupTask;
import org.jfxcore.gradle.tasks.MarkupTask;
import org.jfxcore.gradle.tasks.ProcessMarkupTask;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class CompilerPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        // For each source set, add the corresponding generated sources directory, so it can be
        // picked up by the Java compiler.
        var pathHelper = new PathHelper(project);
        for (SourceSet sourceSet : pathHelper.getSourceSets()) {
            sourceSet.getJava().srcDir(pathHelper.getGeneratedSourcesDir(sourceSet));
        }

        // Register the CompilerService for this project, which is used by the plugin tasks.
        CompilerService.register(project);

        // Configure parseMarkup to run before, and compileMarkup to run after the source code is compiled.
        project.afterEvaluate(this::configureTaskDependencies);

        // Additional configuration is done if the tasks are included in the task graph.
        project.getGradle().getTaskGraph().whenReady(graph -> {
            for (SourceSet sourceSet : pathHelper.getSourceSets()) {
                String processTaskName = sourceSet.getTaskName(ProcessMarkupTask.VERB, MarkupTask.TARGET);
                String compileTaskName = sourceSet.getTaskName(CompileMarkupTask.VERB, MarkupTask.TARGET);

                if (graph.hasTask(project.getPath() + ":" + processTaskName) &&
                        graph.hasTask(project.getPath() + ":" + compileTaskName)) {
                    ExceptionHelper.run(project, sourceSet,
                        () -> configureTasks(project, sourceSet, processTaskName, compileTaskName));
                }
            }
        });
    }

    private void configureTasks(Project project, SourceSet sourceSet,
                                String processTaskName, String compileTaskName) throws Throwable {
        var pathHelper = new PathHelper(project);
        var compiler = CompilerService.get(project).newCompiler(
            sourceSet, pathHelper.getGeneratedSourcesDir(sourceSet), project.getLogger());

        Map<File, Set<File>> markupFileSets = pathHelper.getMarkupFileSets(sourceSet);
        List<File> markupFiles = markupFileSets.values().stream().flatMap(Set::stream).toList();
        Set<Path> generatedJavaFiles = new HashSet<>();

        for (var entry : markupFileSets.entrySet()) {
            for (var file : entry.getValue()) {
                Path generatedFile = compiler.addFile(entry.getKey(), file);
                if (generatedFile != null) {
                    generatedJavaFiles.add(generatedFile);
                }
            }
        }

        this.<ProcessMarkupTask>configureTask(project, processTaskName, task -> {
            task.getSourceSet().set(sourceSet);
            task.getInputs().files(markupFiles);
            task.getOutputs().files(generatedJavaFiles);
            task.getOutputs().upToDateWhen(spec -> generatedJavaFiles.stream().allMatch(Files::exists));
        });

        this.<CompileMarkupTask>configureTask(project, compileTaskName, task -> {
            task.getGeneratedJavaFiles().set(generatedJavaFiles);
        });
    }

    private void configureTaskDependencies(Project project) {
        List<Task> dependentJarTasks =  project.getConfigurations().stream()
            .flatMap(dep -> dep.getDependencies().stream())
            .filter(dep -> dep instanceof ProjectDependency)
            .map(dep -> (ProjectDependency)dep)
            .map(ProjectDependency::getDependencyProject)
            .distinct()
            .flatMap(dp -> dp.getTasksByName("jar", false).stream())
            .toList();

        for (SourceSet sourceSet : new PathHelper(project).getSourceSets()) {
            ExceptionHelper.run(project, sourceSet,
                () -> configureTaskDependenciesForSourceSet(project, sourceSet, dependentJarTasks));
        }
    }

    private void configureTaskDependenciesForSourceSet(
            Project project, SourceSet sourceSet, List<Task> dependentJarTasks) {
        Task processMarkupTask = project.getTasks().create(
            sourceSet.getTaskName(ProcessMarkupTask.VERB, MarkupTask.TARGET), ProcessMarkupTask.class, task -> {
                for (Task jarTask : dependentJarTasks) {
                    task.dependsOn(jarTask);
                }
            });

        Task compileMarkupTask = project.getTasks().create(
            sourceSet.getTaskName(CompileMarkupTask.VERB, MarkupTask.TARGET), CompileMarkupTask.class, task -> {
                task.getSourceSet().set(sourceSet);
                task.onlyIf(spec -> processMarkupTask.getDidWork());
            });

        Task classesTask = getTask(project, sourceSet.getClassesTaskName());

        for (String target : new String[] { "java", "kotlin", "scala", "groovy" }) {
            String compileTaskName = sourceSet.getTaskName("compile", target);
            Task compileTask = project.getTasks().findByName(compileTaskName);

            if (compileTask != null) {
                compileTask.dependsOn(processMarkupTask);
                compileMarkupTask.dependsOn(compileTask);
                classesTask.dependsOn(compileMarkupTask);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends Task> T getTask(Project project, String name) {
        return (T)project.getTasksByName(name, false).stream()
            .findFirst()
            .orElseThrow(() -> new GradleException("Task not found: " + name));
    }

    private <T extends Task> void configureTask(Project project, String name, Consumer<T> spec) {
        spec.accept(getTask(project, name));
    }

}
