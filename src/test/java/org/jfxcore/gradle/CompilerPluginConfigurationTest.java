// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.gradle;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.FileCollectionDependency;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.testfixtures.ProjectBuilder;
import org.jfxcore.gradle.tasks.FxmlSourceInfo;
import org.jfxcore.gradle.tasks.ProcessFxmlTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;

class CompilerPluginConfigurationTest {

    @TempDir
    Path projectDir;

    @Test
    void createsExtensionWithDocumentedConventions() {
        Project project = configuredProject();

        CompilerPluginExtension extension = project.getExtensions().getByType(CompilerPluginExtension.class);

        assertAll(
            () -> assertSame(extension, project.getExtensions().getByName("fxml")),
            () -> assertFalse(extension.getAnnotationProcessing().get()),
            () -> assertEquals(List.of("fxml"), extension.getSourceFileExtensions().get()));
    }

    @Test
    void registersTasksForMainTestAndCustomSourceSets() {
        Project project = configuredProject();
        SourceSetContainer sourceSets = sourceSets(project);
        SourceSet custom = sourceSets.create("integrationTest");

        assertAll(
            () -> assertInstanceOf(ProcessFxmlTask.class, project.getTasks().getByName("processFxml")),
            () -> assertInstanceOf(ProcessFxmlTask.class, project.getTasks().getByName("processTestFxml")),
            () -> assertInstanceOf(ProcessFxmlTask.class, project.getTasks().getByName("processIntegrationTestFxml")),
            () -> assertSourceSetLayout(project, sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)),
            () -> assertSourceSetLayout(project, sourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME)),
            () -> assertSourceSetLayout(project, custom));
    }

    @Test
    void configuresSourceSetCreatedAfterFxmlPluginApplication() {
        Project project = configuredProject();
        SourceSet late = sourceSets(project).create("late");

        assertSourceSetLayout(project, late);
        assertNotNull(project.getTasks().findByName("processLateFxml"));
    }

    @Test
    void followsCompileClasspathAndSearchPathChanges() {
        Project project = configuredProject();
        SourceSet main = sourceSets(project).getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        ProcessFxmlTask task = processTask(project, main);
        File first = projectDir.resolve("dependencies/first.jar").toFile();
        File second = projectDir.resolve("dependencies/second.jar").toFile();

        main.setCompileClasspath(project.files(first));
        assertEquals(Set.of(first), task.getCompileClasspath().get().getFiles());
        assertEquals(Set.of(first), task.getSearchPath().get().getFiles());

        main.setCompileClasspath(project.files(first, second));
        assertEquals(Set.of(first, second), task.getCompileClasspath().get().getFiles());
        assertEquals(Set.of(first, second), task.getSearchPath().get().getFiles());
    }

    @Test
    void outputDirectoriesFollowLateBuildDirectoryChange() {
        Project project = configuredProject();
        SourceSet main = sourceSets(project).getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        ProcessFxmlTask task = processTask(project, main);
        JavaCompile compileJava = javaCompile(project, main);
        CompilerArgumentsProvider arguments = compilerArguments(compileJava);

        project.getLayout().getBuildDirectory().set(project.getLayout().getProjectDirectory().dir("out"));

        assertAll(
            () -> assertEquals(file("out/classes/java/main"), task.getClassesDir().get().getAsFile()),
            () -> assertEquals(file("out/generated/sources/fxml/java/main"), task.getGeneratedSourcesDir().get().getAsFile()),
            () -> assertEquals(file("out/fxml/default/main"), task.getIntermediateBuildDir().get().getAsFile()),
            () -> assertEquals(file("out/fxml/annotationProcessor/main"), arguments.getIntermediateBuildDir().get().getAsFile()));
    }

    @Test
    void classesDirectoryFollowsLateJavaCompileDestinationChange() {
        Project project = configuredProject();
        SourceSet main = sourceSets(project).getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        ProcessFxmlTask task = processTask(project, main);
        JavaCompile compileJava = javaCompile(project, main);

        compileJava.getDestinationDirectory().set(project.getLayout().getProjectDirectory().dir("compiled-elsewhere"));

        assertEquals(file("compiled-elsewhere"), task.getClassesDir().get().getAsFile());
    }

    @Test
    void sourceDirectoriesAddedAfterTaskRealizationBecomeInputs() throws IOException {
        Project project = configuredProject();
        SourceSet main = sourceSets(project).getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        ProcessFxmlTask task = processTask(project, main);
        Path javaSource = write("late-java/Nested/JavaView.fxml", "java");
        Path allSource = write("late-all/AllView.fxml", "all");

        main.getJava().srcDir(projectDir.resolve("late-java"));
        main.getAllSource().srcDir(projectDir.resolve("late-all"));

        assertEquals(Set.of(javaSource.toFile(), allSource.toFile()), allFxmlFiles(task));
    }

    @Test
    void selectsDefaultExtensionAndExcludesUnrelatedFiles() throws IOException {
        Project project = configuredProject();
        SourceSet main = sourceSets(project).getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        Path sourceRoot = projectDir.resolve("src/main/java");
        Path lowerCase = write("src/main/java/View.fxml", "fxml");
        Path upperCase = write("src/main/java/nested/Other.FXML", "fxml");
        write("src/main/java/View.java", "class View {}");
        write("src/main/java/View.fxml.tmp", "temporary");
        write("src/main/java/View.xml", "xml");

        ProcessFxmlTask task = processTask(project, main);

        assertEquals(Set.of(lowerCase.toFile(), upperCase.toFile()), filesFor(task, sourceRoot));
    }

    @Test
    void acceptsConfiguredExtensionsWithAndWithoutLeadingDot() throws IOException {
        Project project = configuredProject();
        CompilerPluginExtension extension = project.getExtensions().getByType(CompilerPluginExtension.class);
        extension.getSourceFileExtensions().set(List.of("fxmlx", ".view"));
        Path first = write("src/main/java/First.FXMLX", "first");
        Path second = write("src/main/java/nested/Second.view", "second");
        write("src/main/java/Ignored.fxml", "ignored");

        assertEquals(
            Set.of(first.toFile(), second.toFile()),
            allFxmlFiles(processTask(project, sourceSets(project).getByName("main"))));
    }

    @Test
    void emptyExtensionListMatchesNoFiles() throws IOException {
        Project project = configuredProject();
        project.getExtensions().getByType(CompilerPluginExtension.class).getSourceFileExtensions().set(List.of());
        write("src/main/java/View.fxml", "fxml");

        assertTrue(allFxmlFiles(processTask(project, sourceSets(project).getByName("main"))).isEmpty());
    }

    @Test
    void excludesGeneratedSourceDirectory() throws IOException {
        Project project = configuredProject();
        SourceSet main = sourceSets(project).getByName("main");
        ProcessFxmlTask task = processTask(project, main);
        Path generated = task.getGeneratedSourcesDir().get().getAsFile().toPath();
        Path real = write("src/main/java/Real.fxml", "real");
        Files.createDirectories(generated);
        Files.writeString(generated.resolve("Generated.fxml"), "generated");

        assertEquals(Set.of(real.toFile()), allFxmlFiles(task));
        assertTrue(task.getFxmlSourceInfo().get().stream()
            .noneMatch(info -> info.getSourceDir().get().getAsFile().equals(generated.toFile())));
    }

    @Test
    void realizedFileCollectionTracksAddRenameAndDelete() throws IOException {
        Project project = configuredProject();
        SourceSet main = sourceSets(project).getByName("main");
        ProcessFxmlTask task = processTask(project, main);
        Path sourceRoot = projectDir.resolve("src/main/java");
        Files.createDirectories(sourceRoot);
        FxmlSourceInfo sourceInfo = sourceInfo(task, sourceRoot);
        Path first = Files.writeString(sourceRoot.resolve("First.fxml"), "first");

        assertEquals(Set.of(first.toFile()), sourceInfo.getFxmlFiles().get().getFiles());

        Path renamed = sourceRoot.resolve("Renamed.FXML");
        Files.move(first, renamed);
        assertEquals(Set.of(renamed.toFile()), sourceInfo.getFxmlFiles().get().getFiles());

        Files.delete(renamed);
        assertTrue(sourceInfo.getFxmlFiles().get().isEmpty());
    }

    @Test
    void wiresAllExpectedCompileTaskNamesIncludingLateTasks() {
        Project project = configuredProject();
        SourceSet main = sourceSets(project).getByName("main");
        ProcessFxmlTask processFxml = processTask(project, main);
        var kotlin = project.getTasks().register("compileKotlin").get();
        var scala = project.getTasks().register("compileScala").get();
        var groovy = project.getTasks().register("compileGroovy").get();

        assertAll(
            () -> assertDependsOn(javaCompile(project, main), processFxml),
            () -> assertDependsOn(kotlin, processFxml),
            () -> assertDependsOn(scala, processFxml),
            () -> assertDependsOn(groovy, processFxml));
    }

    @Test
    void flattenedFxmlInputsDoNotInferDependencyOnJavaCompilation() {
        Project project = configuredProject();
        SourceSet main = sourceSets(project).getByName("main");
        ProcessFxmlTask processFxml = processTask(project, main);
        JavaCompile compileJava = javaCompile(project, main);

        assertFalse(processFxml.getTaskDependencies().getDependencies(processFxml).contains(compileJava));
        assertDependsOn(compileJava, processFxml);
    }

    @Test
    void wiresMainTestAndCustomJavaCompileTasks() {
        Project project = configuredProject();
        SourceSet custom = sourceSets(project).create("integrationTest");

        for (SourceSet sourceSet : List.of(sourceSets(project).getByName("main"),
                                           sourceSets(project).getByName("test"), custom)) {
            assertDependsOn(javaCompile(project, sourceSet), processTask(project, sourceSet));
            assertEquals(1, javaCompile(project, sourceSet).getOptions().getCompilerArgumentProviders().stream()
                .filter(CompilerArgumentsProvider.class::isInstance)
                .count());
        }
    }

    @Test
    void disabledAnnotationProcessingAddsNoDependencyOrArguments() {
        Project project = configuredProject();
        SourceSet main = sourceSets(project).getByName("main");
        Configuration configuration = project.getConfigurations().getByName(main.getAnnotationProcessorConfigurationName());

        assertTrue(configuration.getDependencies().isEmpty());
        assertEquals(List.of(), toList(compilerArguments(javaCompile(project, main)).asArguments()));
    }

    @Test
    void enabledAnnotationProcessingAddsCompilerAndSourceSetSpecificArguments() {
        Project project = configuredProject();
        SourceSet main = sourceSets(project).getByName("main");
        project.getExtensions().getByType(CompilerPluginExtension.class).getAnnotationProcessing().set(true);
        Configuration configuration = project.getConfigurations()
            .getByName(main.getAnnotationProcessorConfigurationName());

        List<FileCollectionDependency> compilerDependencies = configuration.getDependencies().stream()
            .filter(FileCollectionDependency.class::isInstance)
            .map(FileCollectionDependency.class::cast)
            .toList();

        CompilerArgumentsProvider arguments = compilerArguments(javaCompile(project, main));
        List<String> values = toList(arguments.asArguments());

        assertAll(
            () -> assertEquals(1, compilerDependencies.size()),
            () -> assertTrue(compilerDependencies.get(0).getFiles().getFiles().stream().anyMatch(File::exists)),
            () -> assertEquals(3, values.size()),
            () -> assertTrue(values.get(0).startsWith("-Aorg.jfxcore.compiler.processor.sourceDirs=")),
            () -> assertTrue(values.get(0).contains(file("src/main/java").getAbsolutePath())),
            () -> assertTrue(values.get(2).endsWith(file("build/fxml/annotationProcessor/main").getAbsolutePath())));
    }

    @Test
    void fxmlPluginMayBeAppliedBeforeJavaPlugin() {
        Project project = project();
        project.getPluginManager().apply(CompilerPlugin.class);
        project.getPluginManager().apply("java");

        assertDoesNotThrow(() -> ((ProjectInternal)project).evaluate());
        assertNotNull(project.getTasks().findByName("processFxml"));
    }

    @Test
    void projectWithoutJavaFailsWithClearMessage() {
        Project project = project();
        project.getPluginManager().apply(CompilerPlugin.class);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> ((ProjectInternal)project).evaluate());

        assertTrue(allMessages(exception).contains("requires the Java plugin"));
    }

    private Project configuredProject() {
        Project project = project();
        project.getPluginManager().apply("java");
        project.getPluginManager().apply(CompilerPlugin.class);
        return project;
    }

    private Project project() {
        return ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build();
    }

    private static SourceSetContainer sourceSets(Project project) {
        return project.getExtensions().getByType(SourceSetContainer.class);
    }

    private static ProcessFxmlTask processTask(Project project, SourceSet sourceSet) {
        return (ProcessFxmlTask)project.getTasks().getByName(sourceSet.getTaskName("process", "fxml"));
    }

    private static JavaCompile javaCompile(Project project, SourceSet sourceSet) {
        return (JavaCompile)project.getTasks().getByName(sourceSet.getCompileJavaTaskName());
    }

    private static CompilerArgumentsProvider compilerArguments(JavaCompile task) {
        return task.getOptions().getCompilerArgumentProviders().stream()
            .filter(CompilerArgumentsProvider.class::isInstance)
            .map(CompilerArgumentsProvider.class::cast)
            .findFirst()
            .orElseThrow();
    }

    private void assertSourceSetLayout(Project project, SourceSet sourceSet) {
        ProcessFxmlTask task = processTask(project, sourceSet);
        String name = sourceSet.getName();

        assertAll(
            () -> assertEquals(file("build/classes/java/" + name), task.getClassesDir().get().getAsFile()),
            () -> assertEquals(file("build/generated/sources/fxml/java/" + name), task.getGeneratedSourcesDir().get().getAsFile()),
            () -> assertEquals(file("build/fxml/default/" + name), task.getIntermediateBuildDir().get().getAsFile()),
            () -> assertTrue(sourceSet.getJava().getSrcDirs().contains(task.getGeneratedSourcesDir().get().getAsFile())),
            () -> assertNotEquals(task.getIntermediateBuildDir().get().getAsFile(),
                compilerArguments(javaCompile(project, sourceSet)).getIntermediateBuildDir().get().getAsFile()));
    }

    private static void assertDependsOn(org.gradle.api.Task task, org.gradle.api.Task expectedDependency) {
        assertTrue(task.getTaskDependencies().getDependencies(task).contains(expectedDependency),
            () -> task.getPath() + " does not depend on " + expectedDependency.getPath());
    }

    private static Set<File> allFxmlFiles(ProcessFxmlTask task) {
        return task.getFxmlSourceInfo().get().stream()
            .map(FxmlSourceInfo::getFxmlFiles)
            .map(property -> property.get().getFiles())
            .flatMap(Collection::stream)
            .collect(Collectors.toSet());
    }

    private static Set<File> filesFor(ProcessFxmlTask task, Path sourceRoot) {
        return sourceInfo(task, sourceRoot).getFxmlFiles().get().getFiles();
    }

    private static FxmlSourceInfo sourceInfo(ProcessFxmlTask task, Path sourceRoot) {
        return task.getFxmlSourceInfo().get().stream()
            .filter(info -> info.getSourceDir().get().getAsFile().equals(sourceRoot.toFile()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No source info for " + sourceRoot));
    }

    private Path write(String relativePath, String content) throws IOException {
        Path file = projectDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, content);
    }

    private File file(String relativePath) {
        return projectDir.resolve(relativePath).toFile();
    }

    private static List<String> toList(Iterable<String> values) {
        return StreamSupport.stream(values.spliterator(), false).toList();
    }

    private static String allMessages(Throwable throwable) {
        StringBuilder result = new StringBuilder();

        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current.getMessage() != null) {
                result.append(current.getMessage()).append('\n');
            }
        }

        return result.toString();
    }
}
