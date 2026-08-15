// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.gradle;

import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;

class CompilerArgumentsProviderTest {

    @TempDir
    Path projectDir;

    private Project project;

    @BeforeEach
    void createProject() {
        project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build();
    }

    @Test
    void disabledProviderReturnsNoArguments() {
        CompilerArgumentsProvider provider = provider(
            CompilerArgumentsProvider.Target.JAVA,
            false,
            project.files(projectDir.resolve("src")),
            project.files(projectDir.resolve("classes")),
            projectDir.resolve("intermediate"));

        assertAll(
            () -> assertFalse(provider.getEnabled().get()),
            () -> assertEquals(List.of(), provider.getSourceDirLayout()),
            () -> assertTrue(provider.getSearchPath().isEmpty()),
            () -> assertEquals(List.of(), asList(provider.asArguments())));
    }

    @Test
    void javaProviderEmitsProcessorOptionsWithAPrefix() {
        File sourceDir = projectDir.resolve("src/main/java").toFile();
        File searchEntry = projectDir.resolve("library.jar").toFile();
        Path intermediateDir = projectDir.resolve("build/fxml/annotationProcessor/main");
        CompilerArgumentsProvider provider = provider(
            CompilerArgumentsProvider.Target.JAVA,
            true,
            project.files(sourceDir),
            project.files(searchEntry),
            intermediateDir);

        assertEquals(List.of(
            "-Aorg.jfxcore.compiler.processor.sourceDirs=" + sourceDir.getAbsolutePath(),
            "-Aorg.jfxcore.compiler.processor.searchPath=" + searchEntry.getAbsolutePath(),
            "-Aorg.jfxcore.compiler.processor.intermediateBuildDir=" + intermediateDir.toFile().getAbsolutePath()
        ), asList(provider.asArguments()));

        assertEquals(List.of("project:src/main/java"), provider.getSourceDirLayout());
    }

    @Test
    void kotlinProviderEmitsTheSameLogicalOptionsWithoutAPrefix() {
        File sourceDir = projectDir.resolve("src/main/kotlin").toFile();
        File searchEntry = projectDir.resolve("classes").toFile();
        Path intermediateDir = projectDir.resolve("build/fxml/ksp/main");
        CompilerArgumentsProvider provider = provider(
            CompilerArgumentsProvider.Target.KOTLIN,
            true,
            project.files(sourceDir),
            project.files(searchEntry),
            intermediateDir);

        assertEquals(List.of(
            "org.jfxcore.compiler.processor.sourceDirs=" + sourceDir.getAbsolutePath(),
            "org.jfxcore.compiler.processor.searchPath=" + searchEntry.getAbsolutePath(),
            "org.jfxcore.compiler.processor.intermediateBuildDir=" + intermediateDir.toFile().getAbsolutePath()
        ), asList(provider.asArguments()));
    }

    @Test
    void multiplePathsRetainInsertionOrderAndUseThePlatformSeparator() {
        File firstSource = projectDir.resolve("first-source").toFile();
        File secondSource = projectDir.resolve("second-source").toFile();
        File firstSearchEntry = projectDir.resolve("first.jar").toFile();
        File secondSearchEntry = projectDir.resolve("second.jar").toFile();
        CompilerArgumentsProvider provider = provider(
            CompilerArgumentsProvider.Target.JAVA,
            true,
            project.files(firstSource, secondSource),
            project.files(firstSearchEntry, secondSearchEntry),
            projectDir.resolve("intermediate"));

        assertEquals(
            "-Aorg.jfxcore.compiler.processor.sourceDirs="
                + firstSource.getAbsolutePath() + File.pathSeparator + secondSource.getAbsolutePath(),
            asList(provider.asArguments()).get(0));

        assertEquals(
            "-Aorg.jfxcore.compiler.processor.searchPath="
                + firstSearchEntry.getAbsolutePath() + File.pathSeparator + secondSearchEntry.getAbsolutePath(),
            asList(provider.asArguments()).get(1));

        assertEquals(
            List.of("project:first-source", "project:second-source"),
            provider.getSourceDirLayout());
    }

    @Test
    void gettersExposeTheSuppliedCollectionsAndDirectory() {
        FileCollection sourceDirs = project.files(projectDir.resolve("src"));
        FileCollection searchPath = project.files(projectDir.resolve("search"));
        Path intermediateDir = projectDir.resolve("intermediate");
        CompilerArgumentsProvider provider = provider(
            CompilerArgumentsProvider.Target.JAVA, true, sourceDirs, searchPath, intermediateDir);

        assertSame(sourceDirs, provider.getSourceDirs());
        assertSame(searchPath, provider.getSearchPath());
        assertTrue(provider.getEnabled().get());
        assertEquals(intermediateDir.toFile(), provider.getIntermediateBuildDir().get().getAsFile());
    }

    private CompilerArgumentsProvider provider(
            CompilerArgumentsProvider.Target target,
            boolean enabled,
            FileCollection sourceDirs,
            FileCollection searchPath,
            Path intermediateDir) {
        Provider<Directory> directory = project.provider(
            () -> project.getLayout().getProjectDirectory()
                .dir(projectDir.relativize(intermediateDir).toString()));

        return new CompilerArgumentsProvider(
            target, project.getObjects(), project.provider(() -> enabled),
            sourceDirs, searchPath, directory, project.getLayout().getProjectDirectory());
    }

    private static List<String> asList(Iterable<String> values) {
        return StreamSupport.stream(values.spliterator(), false).toList();
    }
}
