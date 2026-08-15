// Copyright (c) 2026, JFXcore. All rights reserved.
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

class AnnotationProcessingFunctionalTest {

    @TempDir
    Path projectDir;

    @Test
    void annotationProcessingIsConditionalAndSourceSetIsolated() throws IOException {
        copyFixture("annotation-processing", projectDir);

        var disabled = build(projectDir, "classes", "integrationTestClasses");

        assertOutcome(disabled, ":compileJava", TaskOutcome.SUCCESS);
        assertOutcome(disabled, ":compileIntegrationTestJava", TaskOutcome.SUCCESS);
        assertFalse(Files.exists(descriptor("main", "InlineViewBase")));
        assertFalse(Files.exists(descriptor("integrationTest", "IntegrationInlineViewBase")));

        Path buildScript = projectDir.resolve("build.gradle.kts");

        Files.writeString(
            buildScript,
            Files.readString(buildScript).replace("annotationProcessing = false", "annotationProcessing = true"));

        Files.writeString(
            source("main", "InlineView.java"),
            enabledSource("InlineView"));

        Files.writeString(
            source("integrationTest", "IntegrationInlineView.java"),
            enabledSource("IntegrationInlineView"));

        var enabled = build(projectDir, "classes", "integrationTestClasses");

        assertOutcome(enabled, ":compileJava", TaskOutcome.SUCCESS);
        assertOutcome(enabled, ":compileIntegrationTestJava", TaskOutcome.SUCCESS);
        assertTrue(Files.isRegularFile(descriptor("main", "InlineViewBase")));
        assertTrue(Files.isRegularFile(descriptor("integrationTest", "IntegrationInlineViewBase")));
        assertTrue(Files.isRegularFile(generated("main", "InlineViewBase")));
        assertTrue(Files.isRegularFile(generated("integrationTest", "IntegrationInlineViewBase")));
        assertCompiledClass(compiled("main", "InlineViewBase"));
        assertCompiledClass(compiled("integrationTest", "IntegrationInlineViewBase"));
    }

    @Test
    void unrelatedGeneratedAllSourceDirectoryRequiresNoProducerWiring() throws IOException {
        copyFixture("unrelated-generated-source", projectDir);

        var result = build(projectDir, "classes");

        assertOutcome(result, ":generateSources", TaskOutcome.SUCCESS);
        assertOutcome(result, ":processFxml", TaskOutcome.SUCCESS);
        assertOutcome(result, ":compileJava", TaskOutcome.SUCCESS);

        Files.writeString(
            projectDir.resolve("build/generated/sources/external/main/Generated.source"),
            "externally modified");

        var regenerated = build(projectDir, "classes");

        assertOutcome(regenerated, ":generateSources", TaskOutcome.SUCCESS);
        assertOutcome(regenerated, ":processFxml", TaskOutcome.UP_TO_DATE);
        assertOutcome(regenerated, ":compileJava", TaskOutcome.UP_TO_DATE);
    }

    private Path source(String sourceSet, String fileName) {
        return projectDir.resolve("src/" + sourceSet + "/java/test/" + fileName);
    }

    private Path descriptor(String sourceSet, String name) {
        return projectDir.resolve("build/fxml/annotationProcessor/" + sourceSet + "/test/" + name + ".fxmd");
    }

    private Path generated(String sourceSet, String name) {
        return projectDir.resolve(
            "build/generated/sources/annotationProcessor/java/" + sourceSet + "/test/" + name + ".java");
    }

    private Path compiled(String sourceSet, String name) {
        return projectDir.resolve("build/classes/java/" + sourceSet + "/test/" + name + ".class");
    }

    private static String enabledSource(String className) {
        return """
            package test;

            import javafx.scene.layout.Pane;
            import org.jfxcore.markup.ComponentView;

            @ComponentView("<Pane/>")
            public class %1$s extends %1$sBase {
                public %1$s() {
                    initializeComponent();
                }
            }
        """.formatted(className);
    }
}
