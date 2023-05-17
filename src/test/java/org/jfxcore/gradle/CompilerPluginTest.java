// Copyright (c) 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.gradle;

import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

public class CompilerPluginTest {

    @Test
    void nonModularProject() {
        var runner = GradleRunner.create()
            .withProjectDir(new File("test-project/non-modular"))
            .withGradleVersion("8.0")
            .withArguments("clean", "build", "--stacktrace")
            .forwardOutput()
            .build();

        assertEquals(TaskOutcome.SUCCESS, runner.task(":build").getOutcome());
    }

    @Test
    void modularProject() {
        var runner = GradleRunner.create()
            .withProjectDir(new File("test-project/modular"))
            .withGradleVersion("8.0")
            .withArguments("clean", "build", "--stacktrace")
            .forwardOutput()
            .build();

        assertEquals(TaskOutcome.SUCCESS, runner.task(":build").getOutcome());
    }

}
