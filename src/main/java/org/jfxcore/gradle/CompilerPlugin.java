// Copyright (c) 2023, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskCollection;
import org.gradle.jvm.tasks.Jar;
import org.gradle.util.GradleVersion;
import org.jfxcore.gradle.compiler.CompilerService;
import org.jfxcore.gradle.tasks.ProcessFxmlTask;
import java.io.File;
import java.util.List;
import java.util.stream.Stream;

public class CompilerPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        // For each source set, add the corresponding generated sources directory, so it can be
        // picked up by the Java compiler.
        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);

        sourceSets.configureEach(sourceSet ->
            sourceSet.getJava().srcDir(PathHelper.getGeneratedSourcesDir(project, sourceSet)));

        project.getGradle().getSharedServices().registerIfAbsent(
            CompilerService.NAME, CompilerService.class, spec -> {});

        CompilerService.register(project);

        List<TaskCollection<Jar>> jarTaskDependencies = getJarTaskDependencies(project);

        sourceSets.configureEach(sourceSet ->
            configureTasksForSourceSet(project, sourceSet, jarTaskDependencies));
    }

    private List<TaskCollection<Jar>> getJarTaskDependencies(Project project) {
        Stream<ProjectDependency> projectDependencies = project.getConfigurations().stream()
            .flatMap(config -> config.getDependencies().stream())
            .filter(dep -> dep instanceof ProjectDependency)
            .map(dep -> (ProjectDependency)dep);

        Stream<Project> projects;

        if (GradleVersion.current().compareTo(GradleVersion.version("8.11")) >= 0) {
            projects = projectDependencies
                .map(ProjectDependency::getPath)
                .map(project::project)
                .distinct();
        } else {
            projects = projectDependencies
                .map(ProjectDependency::getDependencyProject)
                .distinct();
        }

        return projects
            .map(dp -> dp.getTasks().withType(Jar.class).matching(jar -> jar.getName().equals("jar")))
            .toList();
    }

    private void configureTasksForSourceSet(
            Project project, SourceSet sourceSet, List<TaskCollection<Jar>> jarTaskDependencies) {
        ConfigurableFileCollection searchPath = project.getObjects().fileCollection();
        searchPath.from(sourceSet.getOutput());
        searchPath.from(sourceSet.getCompileClasspath());

        FileCollection srcDirs = project.files(sourceSet.getAllSource().getSrcDirs());
        File classesDir = sourceSet.getJava().getClassesDirectory().get().getAsFile();
        File genSrcDir = PathHelper.getGeneratedSourcesDir(project, sourceSet);

        Provider<ProcessFxmlTask> processFxmlTask = project.getTasks().register(
            sourceSet.getTaskName(ProcessFxmlTask.VERB, ProcessFxmlTask.TARGET),
            ProcessFxmlTask.class, task -> {
                task.getSearchPath().set(searchPath);
                task.getSourceDirs().set(srcDirs);
                task.getClassesDir().set(classesDir);
                task.getGeneratedSourcesDir().set(genSrcDir);
                jarTaskDependencies.forEach(task::dependsOn);
            });

        // Run the FXML compiler at the end of compileJava's action list. This is important for
        // incremental compilation: Gradle will fingerprint the compiled class files after the
        // last task action is executed, i.e. after the FXML compiler has rewritten the bytecode.
        project.getTasks().named(sourceSet.getCompileJavaTaskName(), task -> task.doLast(
            project.getObjects().newInstance(
                RunCompilerAction.class, searchPath, srcDirs,
                classesDir, genSrcDir, project.getLogger())));

        for (String target : new String[] { "java", "kotlin", "scala", "groovy" }) {
            String compileTaskName = sourceSet.getTaskName("compile", target);
            Task compileTask = project.getTasks().findByName(compileTaskName);
            if (compileTask != null) {
                compileTask.dependsOn(processFxmlTask);
            }
        }
    }
}
