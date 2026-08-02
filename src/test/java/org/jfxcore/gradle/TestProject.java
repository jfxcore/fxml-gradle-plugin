// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class TestProject {

    static final String MINIMUM_GRADLE_VERSION = "8.10.2";
    static final String WRAPPER_GRADLE_VERSION = "9.3.1";

    private TestProject() {}

    static void copyFixture(String fixtureName, Path destination) throws IOException {
        String resourceName = "/" + fixtureName;
        URL resource = TestProject.class.getResource(resourceName);

        if (resource == null) {
            throw new IOException("Test fixture not found: " + resourceName);
        }

        try {
            if (resource.getProtocol().equals("jar")) {
                URI resourceUri = resource.toURI();
                String uriText = resourceUri.toString();
                int separator = uriText.indexOf("!/");
                URI archiveUri = URI.create(uriText.substring(0, separator));
                FileSystem fileSystem;
                boolean closeFileSystem = false;

                try {
                    fileSystem = FileSystems.getFileSystem(archiveUri);
                } catch (FileSystemNotFoundException ignored) {
                    fileSystem = FileSystems.newFileSystem(archiveUri, Map.of());
                    closeFileSystem = true;
                }

                try {
                    copyDirectory(fileSystem.getPath(uriText.substring(separator + 1)), destination);
                } finally {
                    if (closeFileSystem) {
                        fileSystem.close();
                    }
                }
            } else {
                copyDirectory(Path.of(resource.toURI()), destination);
            }
        } catch (URISyntaxException ex) {
            throw new IOException("Invalid fixture URL: " + resource, ex);
        }
    }

    private static void copyDirectory(Path source, Path destination) throws IOException {
        try (var files = Files.walk(source)) {
            for (Path file : files.toList()) {
                Path target = destination.resolve(source.relativize(file).toString());

                if (Files.isDirectory(file)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    static void copyResource(String resourceName, Path destination) throws IOException {
        try (InputStream resource = TestProject.class.getResourceAsStream("/" + resourceName)) {
            if (resource == null) {
                throw new IOException("Test resource not found: /" + resourceName);
            }

            Files.createDirectories(destination.getParent());
            Files.copy(resource, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static GradleRunner runner(Path projectDir, String... arguments) {
        return createRunner(projectDir, null, arguments);
    }

    static GradleRunner runnerWithVersion(Path projectDir, String gradleVersion, String... arguments) {
        return createRunner(projectDir, gradleVersion, arguments);
    }

    private static GradleRunner createRunner(Path projectDir, String gradleVersion, String... arguments) {
        ArrayList<String> allArguments = new ArrayList<>(Arrays.asList(arguments));
        allArguments.add("--stacktrace");
        allArguments.add("--configuration-cache");
        allArguments.add("--build-cache");

        GradleRunner runner = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withTestKitDir(Path.of("build", "test-kit").toAbsolutePath().toFile())
            .withArguments(allArguments);

        return gradleVersion == null ? runner : runner.withGradleVersion(gradleVersion);
    }

    static BuildResult build(Path projectDir, String... arguments) {
        return runner(projectDir, arguments).build();
    }

    static void assertOutcome(BuildResult result, String taskPath, TaskOutcome outcome) {
        BuildTask task = result.task(taskPath);
        assertNotNull(task, () -> "Task not found in build result: " + taskPath);
        assertEquals(outcome, task.getOutcome(), () -> taskPath + "\n" + result.getOutput());
    }

    static void assertCompiledClass(Path classFile) throws IOException {
        assertTrue(Files.isRegularFile(classFile), () -> "Class file does not exist: " + classFile);
        String contents = Files.readString(classFile, StandardCharsets.ISO_8859_1);
        assertTrue(contents.contains("org.jfxcore.fxml"), () -> "FXML compiler marker not found in " + classFile);
    }
}
