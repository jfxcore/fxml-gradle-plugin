// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.jfxcore.gradle.TestProject.*;
import static org.junit.jupiter.api.Assertions.*;

class KotlinKspFunctionalTest {

    private static final String WARNING = "Kotlin Symbol Processing plugin";

    @TempDir
    Path projectDir;

    @Test
    void realKspBuildWorksForEitherPluginApplicationOrder() throws IOException {
        copyFixture("kotlin-ksp", projectDir);

        BuildResult kspFirst = build(projectDir, "classes", "--info");
        assertSuccessfulKspBuild(kspFirst);

        Path buildScript = projectDir.resolve("build.gradle.kts");
        String script = Files.readString(buildScript);
        Files.writeString(buildScript, script.replace(
            "    id(\"com.google.devtools.ksp\") version \"2.3.10\"\n"
                + "    id(\"org.jfxcore.fxmlplugin\")",
            "    id(\"org.jfxcore.fxmlplugin\")\n"
                + "    id(\"com.google.devtools.ksp\") version \"2.3.10\""));

        build(projectDir, "clean");
        BuildResult fxmlFirst = build(projectDir, "classes", "--info", "--rerun-tasks");
        assertSuccessfulKspBuild(fxmlFirst);
    }

    @Test
    void kotlinWarningIsConditional() throws IOException {
        copyFixture("kotlin-warning", projectDir);

        BuildResult enabledWithoutKsp = build(projectDir, "help");
        assertTrue(enabledWithoutKsp.getOutput().contains(WARNING));

        Path buildScript = projectDir.resolve("build.gradle.kts");
        Files.writeString(buildScript, Files.readString(buildScript)
            .replace("annotationProcessing = true", "annotationProcessing = false"));

        BuildResult disabled = build(projectDir, "help");
        assertFalse(disabled.getOutput().contains(WARNING));

        Files.writeString(buildScript, """
            plugins {
                java
                id("org.jfxcore.fxmlplugin")
            }

            fxml {
                annotationProcessing = true
            }
        """);

        BuildResult noKotlin = build(projectDir, "help");
        assertFalse(noKotlin.getOutput().contains(WARNING));
    }

    private void assertSuccessfulKspBuild(BuildResult result) throws IOException {
        assertOutcome(result, ":processFxml", TaskOutcome.SUCCESS);
        assertOutcome(result, ":kspKotlin", TaskOutcome.SUCCESS);
        assertOutcome(result, ":compileJava", TaskOutcome.SUCCESS);
        assertTrue(taskIndex(result, ":processFxml") < taskIndex(result, ":kspKotlin"));
        assertTrue(taskIndex(result, ":kspKotlin") < taskIndex(result, ":compileJava"));
        assertFalse(result.getOutput().contains(WARNING));
        assertTrue(Files.isRegularFile(projectDir.resolve("build/fxml/ksp/main/test/KotlinViewBase.fxmd")));
        assertTrue(Files.isRegularFile(projectDir.resolve("build/generated/ksp/main/java/test/KotlinViewBase.java")));
        assertCompiledClass(projectDir.resolve("build/classes/java/main/test/KotlinViewBase.class"));
    }

    private static int taskIndex(BuildResult result, String taskPath) {
        for (int i = 0; i < result.getTasks().size(); ++i) {
            if (result.getTasks().get(i).getPath().equals(taskPath)) {
                return i;
            }
        }

        throw new AssertionError("Task not found: " + taskPath);
    }
}
