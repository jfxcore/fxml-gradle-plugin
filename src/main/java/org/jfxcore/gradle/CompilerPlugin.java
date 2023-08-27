// Copyright (c) 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.gradle;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.SourceSet;
import org.jfxcore.gradle.compiler.CompilerService;
import org.jfxcore.gradle.tasks.CompileMarkupTask;
import org.jfxcore.gradle.tasks.MarkupTask;
import org.jfxcore.gradle.tasks.ProcessMarkupTask;

public class CompilerPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        // For each source set, add the corresponding generated sources directory, so it can be
        // picked up by the Java compiler.
        var pathHelper = new PathHelper(project);
        for (SourceSet sourceSet : pathHelper.getSourceSets()) {
            sourceSet.getJava().srcDir(pathHelper.getGeneratedSourcesDir(sourceSet));
        }

        // Register the CompilerService for this build, which is used by the plugin tasks.
        project.getGradle()
            .getSharedServices()
            .registerIfAbsent(CompilerService.class.getName(), CompilerService.class, spec -> {});

        // Configure parseMarkup to run before, and compileMarkup to run after the source code is compiled.
        project.afterEvaluate(p -> {
            for (SourceSet sourceSet : pathHelper.getSourceSets()) {
                String classesTaskName = sourceSet.getClassesTaskName();
                Task classesTask = project.getTasksByName(classesTaskName, false).stream()
                    .findFirst()
                    .orElseThrow(() -> new GradleException("Task not found: " + classesTaskName));

                String processMarkupTaskName = sourceSet.getTaskName(ProcessMarkupTask.VERB, MarkupTask.TARGET);
                Task processMarkupTask = project.getTasks().create(processMarkupTaskName,
                    ProcessMarkupTask.class, task -> task.getSourceSet().set(sourceSet));

                String compileMarkupTaskName = sourceSet.getTaskName(CompileMarkupTask.VERB, MarkupTask.TARGET);
                Task compileMarkupTask = project.getTasks().create(compileMarkupTaskName,
                    CompileMarkupTask.class, task -> task.getSourceSet().set(sourceSet));

                for (String compileTarget : new String[] { "Java", "Kotlin", "Scala", "Groovy" }) {
                    String compileTaskName = sourceSet.getTaskName("compile", compileTarget);
                    Task compileTask = project.getTasks().findByName(compileTaskName);

                    if (compileTask != null) {
                        compileTask.dependsOn(processMarkupTask);
                        compileMarkupTask.dependsOn(compileTask);
                        classesTask.dependsOn(compileMarkupTask);
                    }
                }
            }
        });
    }

}
