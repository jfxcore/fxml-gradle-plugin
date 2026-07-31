// Copyright (c) 2023, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.gradle;

import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.*;

public class CompilerPluginTest {

    @Test
    void nonModularProject() {
        var runner = GradleRunner.create()
            .withProjectDir(new File("test-project/non-modular"))
            .withGradleVersion("9.3.1")
            .withArguments("clean", "build", "--stacktrace")
            .forwardOutput()
            .build();

        assertEquals(TaskOutcome.SUCCESS, runner.task(":build").getOutcome());
    }

    @Test
    void modularProject() {
        var runner = GradleRunner.create()
            .withProjectDir(new File("test-project/modular"))
            .withGradleVersion("9.3.1")
            .withArguments("clean", "build", "--stacktrace")
            .forwardOutput()
            .build();

        assertEquals(TaskOutcome.SUCCESS, runner.task(":build").getOutcome());
    }

    @Test
    void renamedFxmlDoesNotLeaveStaleOutputs(@TempDir Path projectDir) throws IOException {
        copyFixture("settings.gradle.kts", projectDir.resolve("settings.gradle.kts"));
        copyFixture("gradle.properties", projectDir.resolve("gradle.properties"));
        copyFixture("build.gradle.kts", projectDir.resolve("build.gradle.kts"));

        Path sourceDir = Files.createDirectories(projectDir.resolve("src/main/java/test"));
        Path oldJavaFile = sourceDir.resolve("OldName.java");
        Path oldFxmlFile = sourceDir.resolve("OldName.fxmlx");
        copyFixture("OldName.java", oldJavaFile);
        copyFixture("OldName.fxmlx", oldFxmlFile);

        var firstBuild = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withGradleVersion("9.3.1")
            .withArguments("classes", "--stacktrace")
            .build();

        assertEquals(TaskOutcome.SUCCESS, firstBuild.task(":classes").getOutcome());

        Path newJavaFile = sourceDir.resolve("NewName.java");
        Path newFxmlFile = sourceDir.resolve("NewName.fxmlx");
        Files.move(oldJavaFile, newJavaFile);
        Files.move(oldFxmlFile, newFxmlFile);
        copyFixture("NewName.java", newJavaFile);
        copyFixture("NewName.fxmlx", newFxmlFile);

        var secondBuild = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withGradleVersion("9.3.1")
            .withArguments("classes", "--stacktrace")
            .build();

        assertEquals(TaskOutcome.SUCCESS, secondBuild.task(":classes").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, secondBuild.task(":processFxml").getOutcome());
        assertTrue(secondBuild.getOutput().contains("Configuration cache entry reused."));

        Path generatedSources = projectDir.resolve("build/generated/sources/fxml/java/main/test");
        Path descriptors = projectDir.resolve("build/fxml/default/main/test");
        Path classes = projectDir.resolve("build/classes/java/main/test");

        assertAll(
            () -> assertFalse(Files.exists(generatedSources.resolve("TestNameBase.java"))),
            () -> assertTrue(Files.exists(generatedSources.resolve("NewNameBase.java"))),
            () -> assertFalse(Files.exists(descriptors.resolve("TestNameBase.fxmd"))),
            () -> assertTrue(Files.exists(descriptors.resolve("NewNameBase.fxmd"))),
            () -> assertFalse(Files.exists(classes.resolve("TestName.class"))),
            () -> assertFalse(Files.exists(classes.resolve("TestNameBase.class"))),
            () -> assertTrue(Files.exists(classes.resolve("NewName.class"))),
            () -> assertTrue(Files.exists(classes.resolve("NewNameBase.class"))));
    }

    private static void copyFixture(String name, Path destination) throws IOException {
        String resourceName = "/rename-test/" + name;
        InputStream resource = CompilerPluginTest.class.getResourceAsStream(resourceName);

        if (resource == null) {
            throw new IOException("Test fixture not found: " + resourceName);
        }

        Files.createDirectories(destination.getParent());

        try (resource) {
            Files.copy(resource, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
