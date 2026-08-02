// Copyright (c) 2023, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.gradle;

import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.jfxcore.gradle.TestProject.*;
import static org.junit.jupiter.api.Assertions.*;

public class CompilerPluginTest {

    @Test
    void nonModularProject(@TempDir Path projectDir) throws IOException {
        copyFixture("non-modular", projectDir);

        var result = build(projectDir, "classes", "integrationTestClasses");

        assertOutcome(result, ":processFxml", TaskOutcome.SUCCESS);
        assertOutcome(result, ":compileJava", TaskOutcome.SUCCESS);
        assertOutcome(result, ":processIntegrationTestFxml", TaskOutcome.SUCCESS);
        assertOutcome(result, ":compileIntegrationTestJava", TaskOutcome.SUCCESS);
        assertGeneratedArtifacts(projectDir, "main", "org/example/MainViewBase");
        assertGeneratedArtifacts(projectDir, "integrationTest", "org/example/IntegrationViewBase");
    }

    @Test
    void modularProject(@TempDir Path projectDir) throws IOException {
        copyFixture("modular", projectDir);

        var result = build(projectDir, "classes");

        assertOutcome(result, ":processFxml", TaskOutcome.SUCCESS);
        assertOutcome(result, ":compileJava", TaskOutcome.SUCCESS);
        assertGeneratedArtifacts(projectDir, "main", "org/example/MainViewBase");
    }

    private static void assertGeneratedArtifacts(Path projectDir, String sourceSet, String relativeName) {
        Path generatedSource = projectDir.resolve("build/generated/sources/fxml/java/" + sourceSet + "/" + relativeName + ".java");
        Path descriptor = projectDir.resolve("build/fxml/default/" + sourceSet + "/" + relativeName + ".fxmd");
        Path compiledClass = projectDir.resolve("build/classes/java/" + sourceSet + "/" + relativeName + ".class");

        assertAll(
            () -> assertTrue(Files.isRegularFile(generatedSource)),
            () -> assertTrue(Files.isRegularFile(descriptor)),
            () -> assertCompiledClass(compiledClass));
    }
}
