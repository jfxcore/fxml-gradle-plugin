// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.gradle;

import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PathHelperTest {

    @TempDir
    Path tempDir;

    @Test
    void findsDescriptorsRecursivelyAndCaseInsensitively() throws IOException {
        Path rootDescriptor = write("descriptors/Main.fxmd");
        Path nestedDescriptor = write("descriptors/nested/Other.FXMD");
        Files.createDirectories(tempDir.resolve("descriptors/not-a-file.fxmd"));
        write("descriptors/ignored.txt");
        write("descriptors/nested/temporary.fxmd.tmp");

        Set<Path> actual = new HashSet<>(PathHelper.getDescriptorFiles(tempDir.resolve("descriptors").toFile()));

        assertEquals(Set.of(rootDescriptor, nestedDescriptor), actual);
    }

    @Test
    void missingOrNonDirectoryDescriptorRootReturnsNoFiles() throws IOException {
        Path ordinaryFile = write("ordinary-file");

        assertTrue(PathHelper.getDescriptorFiles(tempDir.resolve("missing").toFile()).isEmpty());
        assertTrue(PathHelper.getDescriptorFiles(ordinaryFile.toFile()).isEmpty());
    }

    @Test
    void extractsFileNames() {
        assertEquals("View", PathHelper.getFileNameWithoutExtension(Path.of("nested", "View.fxmd")));
        assertEquals("View.generated", PathHelper.getFileNameWithoutExtension(Path.of("View.generated.fxmd")));
        assertEquals("View", PathHelper.getFileNameWithoutExtension(Path.of("View")));
    }

    @Test
    void constructsLiveGeneratedSourceDirectoryForNamedSourceSet() {
        Project project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();
        project.getPluginManager().apply("java");
        var sourceSet = project.getExtensions().getByType(SourceSetContainer.class).create("integrationTest");
        var generatedDir = PathHelper.getGeneratedSourcesDirectory(project, sourceSet);

        project.getLayout().getBuildDirectory().set(project.getLayout().getProjectDirectory().dir("out"));

        assertEquals(
            tempDir.resolve("out/generated/sources/fxml/java/integrationTest").toFile(),
            generatedDir.get().getAsFile());
    }

    private Path write(String relativePath) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, "test");
    }
}
