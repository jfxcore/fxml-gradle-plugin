// Copyright (c) 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskCollection;
import org.gradle.api.tasks.compile.GroovyCompile;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.scala.ScalaCompile;
import org.jfxcore.gradle.compiler.CompilerService;
import org.jfxcore.gradle.tasks.CompileMarkupTask;
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

        // Configure parseMarkup to run before, and compileMarkup to run after the source code is compiled.
        project.afterEvaluate(p -> {
            var provider = createProvider(project);
            Task processMarkup = project.getTasks().create(ProcessMarkupTask.NAME,
                ProcessMarkupTask.class, task -> task.getCompilerService().set(provider));
            Task compileMarkup = project.getTasks().create(CompileMarkupTask.NAME,
                CompileMarkupTask.class, task -> task.getCompilerService().set(provider));

            TaskCollection<JavaCompile> javaCompileTasks = project.getTasks().withType(JavaCompile.class);
            TaskCollection<GroovyCompile> groovyCompileTasks = project.getTasks().withType(GroovyCompile.class);
            TaskCollection<ScalaCompile> scalaCompileTasks = project.getTasks().withType(ScalaCompile.class);
            TaskCollection<?> kotlinCompileTasks = null;

            try {
                var kotlinCompileClass = Class.forName("org.jetbrains.kotlin.gradle.tasks.KotlinCompile");
                kotlinCompileTasks = project.getTasks().matching(kotlinCompileClass::isInstance);
            } catch (ClassNotFoundException ignored) {
            }

            for (TaskCollection<?> collection : new TaskCollection[] {
                    javaCompileTasks, groovyCompileTasks, scalaCompileTasks, kotlinCompileTasks}) {
                if (collection != null) {
                    for (Task task : collection) {
                        task.dependsOn(processMarkup);
                        task.finalizedBy(compileMarkup);
                        compileMarkup.dependsOn(task);
                    }
                }
            }
        });
    }

    private Provider<CompilerService> createProvider(Project project) {
        return project.getGradle().getSharedServices()
            .registerIfAbsent(
                "compilerService:" + project.getName(), CompilerService.class,
                spec -> spec.getParameters().getCompileClasspath().set(new PathHelper(project).getCompileClasspath()));
    }

}
