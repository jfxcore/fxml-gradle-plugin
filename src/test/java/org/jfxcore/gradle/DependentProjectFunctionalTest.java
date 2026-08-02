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

class DependentProjectFunctionalTest {

    @TempDir
    Path projectDir;

    @Test
    void projectClasspathBuildsDependenciesAndInvalidatesFxmlProcessing() throws IOException {
        copyFixture("fixtures/multi-project", projectDir);

        var initial = build(projectDir, ":app:classes");

        assertOutcome(initial, ":model:compileJava", TaskOutcome.SUCCESS);
        assertOutcome(initial, ":app:processFxml", TaskOutcome.SUCCESS);
        assertOutcome(initial, ":app:compileJava", TaskOutcome.SUCCESS);
        assertTrue(taskIndex(initial, ":model:compileJava") < taskIndex(initial, ":app:processFxml"));
        assertCompiledClass(projectDir.resolve("app/build/classes/java/main/org/example/app/MainViewBase.class"));

        Path modelSource = projectDir.resolve("model/src/main/java/org/example/model/CustomPane.java");

        Files.writeString(modelSource, """
            package org.example.model;

            import javafx.scene.layout.Pane;

            public class CustomPane extends Pane {
                public String dependencyVersion() {
                    return "changed";
                }
            }
        """);

        var changed = build(projectDir, ":app:classes");

        assertOutcome(changed, ":model:compileJava", TaskOutcome.SUCCESS);
        assertOutcome(changed, ":app:processFxml", TaskOutcome.SUCCESS);
        assertOutcome(changed, ":app:compileJava", TaskOutcome.SUCCESS);
        assertTrue(changed.getOutput().contains("Configuration cache entry reused."));
        assertCompiledClass(projectDir.resolve("app/build/classes/java/main/org/example/app/MainViewBase.class"));
    }

    private static int taskIndex(org.gradle.testkit.runner.BuildResult result, String taskPath) {
        for (int i = 0; i < result.getTasks().size(); ++i) {
            if (result.getTasks().get(i).getPath().equals(taskPath)) {
                return i;
            }
        }

        throw new AssertionError("Task not found: " + taskPath);
    }
}
