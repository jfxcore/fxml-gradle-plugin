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

class CompilerErrorRecoveryTest {

    @TempDir
    Path projectDir;

    @Test
    void reportsUsefulErrorsAndRecoversWithoutClean() throws IOException {
        copyFixture("fixtures/lifecycle", projectDir);
        Path fxml = projectDir.resolve("src/main/java/test/MainView.fxmlx");

        Files.writeString(fxml, "<Pane");
        var malformed = runner(projectDir, "classes").buildAndFail();

        assertTrue(
            malformed.getOutput().contains("MainView.fxmlx")
            || malformed.getOutput().toLowerCase().contains("xml"));

        assertFalse(malformed.getOutput().contains("> Internal compiler error"));

        Files.writeString(fxml, """
            <?import missing.DoesNotExist?>

            <DoesNotExist xmlns="http://javafx.com/javafx"
                          xmlns:fx="http://jfxcore.org/fxml/2.0"
                          fx:subclass="test.MainView"/>
        """);

        var missingType = runner(projectDir, "classes").buildAndFail();

        assertTrue(missingType.getOutput().contains("DoesNotExist"));
        assertFalse(missingType.getOutput().contains("> Internal compiler error"));

        Files.writeString(fxml, """
            <?import javafx.scene.layout.*?>

            <Pane xmlns="http://javafx.com/javafx"
                  xmlns:fx="http://jfxcore.org/fxml/2.0"
                  fx:subclass="test.MainView"/>
        """);

        var corrected = build(projectDir, "classes");

        assertOutcome(corrected, ":processFxml", TaskOutcome.SUCCESS);
        assertOutcome(corrected, ":compileJava", TaskOutcome.SUCCESS);
        assertCompiledClass(projectDir.resolve("build/classes/java/main/test/MainViewBase.class"));
    }
}
