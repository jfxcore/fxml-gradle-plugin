// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.gradle;

import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.IOException;
import java.nio.file.Path;

import static org.jfxcore.gradle.TestProject.*;

class GradleCompatibilityTest {

    @TempDir
    Path projectDir;

    @ParameterizedTest(name = "Gradle {0}")
    @ValueSource(strings = { MINIMUM_GRADLE_VERSION, WRAPPER_GRADLE_VERSION })
    void pluginConfiguresAndExecutesAtSupportedVersionBoundaries(String gradleVersion) throws IOException {
        Path versionProjectDir = projectDir.resolve("gradle-" + gradleVersion);
        copyFixture("non-modular", versionProjectDir);

        var result = runnerWithVersion(versionProjectDir, gradleVersion, "classes").build();

        assertOutcome(result, ":processFxml", TaskOutcome.SUCCESS);
        assertOutcome(result, ":compileJava", TaskOutcome.SUCCESS);
        assertCompiledClass(versionProjectDir.resolve("build/classes/java/main/org/example/MainViewBase.class"));
    }
}
