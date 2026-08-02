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
import java.util.Arrays;
import java.util.regex.Pattern;

import static org.jfxcore.gradle.TestProject.*;
import static org.junit.jupiter.api.Assertions.*;

class IncrementalLifecycleTest {

    @TempDir
    Path projectDir;

    @Test
    void tracksTheCompleteIncrementalAndCacheLifecycle() throws IOException {
        copyFixture("fixtures/lifecycle", projectDir);
        Paths paths = new Paths(projectDir);

        BuildResult initial = build(projectDir, "classes");
        assertExecuted(initial);
        assertOutputs(paths, "MainView");
        assertCompiledClass(paths.compiled("MainViewBase"));

        BuildResult unchanged = build(projectDir, "classes");
        assertOutcome(unchanged, ":processFxml", TaskOutcome.UP_TO_DATE);
        assertOutcome(unchanged, ":compileJava", TaskOutcome.UP_TO_DATE);
        assertConfigurationCacheReused(unchanged);

        byte[] firstDescriptor = Files.readAllBytes(paths.descriptor("MainViewBase"));
        Files.writeString(paths.source("MainView.fxmlx"), """
            <?import javafx.scene.layout.*?>

            <Pane xmlns="http://javafx.com/javafx"
                  xmlns:fx="http://jfxcore.org/fxml/2.0"
                  fx:subclass="test.MainView"
                  prefWidth="321"/>
        """);

        BuildResult edited = build(projectDir, "classes");
        assertExecuted(edited);
        assertConfigurationCacheReused(edited);
        assertFalse(Arrays.equals(firstDescriptor, Files.readAllBytes(paths.descriptor("MainViewBase"))));
        assertCompiledClass(paths.compiled("MainViewBase"));

        Files.writeString(paths.source("SecondView.java"), """
            package test;

            public class SecondView extends SecondViewBase {
                public SecondView() {
                    initializeComponent();
                }
            }
        """);

        Files.writeString(paths.source("SecondView.fxmlx"), """
            <?import javafx.scene.layout.*?>

            <Pane xmlns="http://javafx.com/javafx"
                  xmlns:fx="http://jfxcore.org/fxml/2.0"
                  fx:subclass="test.SecondView"/>
        """);

        BuildResult added = build(projectDir, "classes");
        assertExecuted(added);
        assertConfigurationCacheReused(added);
        assertOutputs(paths, "SecondView");

        Files.delete(paths.source("SecondView.java"));
        Files.delete(paths.source("SecondView.fxmlx"));
        Files.writeString(paths.source("RenamedView.java"), """
            package test;

            public class RenamedView extends RenamedViewBase {
                public RenamedView() {
                    initializeComponent();
                }
            }
        """);

        Files.writeString(paths.source("RenamedView.fxmlx"), """
            <?import javafx.scene.layout.*?>

            <Pane xmlns="http://javafx.com/javafx"
                  xmlns:fx="http://jfxcore.org/fxml/2.0"
                  fx:subclass="test.RenamedView"/>
        """);

        BuildResult renamed = build(projectDir, "classes");
        assertExecuted(renamed);
        assertConfigurationCacheReused(renamed);
        assertAll(
            () -> assertMissingOutputs(paths, "SecondView"),
            () -> assertOutputs(paths, "RenamedView"));

        Files.delete(paths.source("RenamedView.java"));
        Files.delete(paths.source("RenamedView.fxmlx"));
        BuildResult deleted = build(projectDir, "classes");
        assertOutcome(deleted, ":processFxml", TaskOutcome.SUCCESS);
        assertOutcome(deleted, ":compileJava", TaskOutcome.FROM_CACHE);
        assertConfigurationCacheReused(deleted);
        assertMissingOutputs(paths, "RenamedView");

        build(projectDir, "clean");
        BuildResult cachePrimingBuild = build(projectDir, "classes", "--info");
        assertOutcome(cachePrimingBuild, ":processFxml", TaskOutcome.SUCCESS);
        assertCompiledClass(paths.compiled("MainViewBase"));

        byte[] generatedSourceAfterPriming = Files.readAllBytes(paths.generated("MainViewBase"));
        byte[] descriptorAfterPriming = Files.readAllBytes(paths.descriptor("MainViewBase"));
        Path localBuildCache = projectDir.resolve(".gradle/build-cache");
        long cachedFilesAfterPriming = countRegularFiles(localBuildCache);
        assertTrue(cachedFilesAfterPriming > 0, "The fixture-local build cache is empty");

        build(projectDir, "clean");
        assertTrue(countRegularFiles(localBuildCache) >= cachedFilesAfterPriming,
            "The clean task removed entries from the fixture-local build cache");

        BuildResult restored = build(projectDir, "classes", "--info");
        assertArrayEquals(
            generatedSourceAfterPriming,
            Files.readAllBytes(paths.generated("MainViewBase")),
            "Generated source changed across clean builds");

        assertArrayEquals(
            descriptorAfterPriming,
            Files.readAllBytes(paths.descriptor("MainViewBase")),
            "FXML descriptor changed across clean builds");

        assertTrue(cachePrimingBuild.getOutput().contains("Build cache key for task ':compileJava' is"));
        assertEquals(
            compileJavaCacheKey(cachePrimingBuild),
            compileJavaCacheKey(restored),
            cachePrimingBuild.getOutput() + "\n--- RESTORED ---\n" + restored.getOutput());

        assertOutcome(restored, ":processFxml", TaskOutcome.SUCCESS);
        assertOutcome(restored, ":compileJava", TaskOutcome.FROM_CACHE);
        assertCompiledClass(paths.compiled("MainViewBase"));

        byte[] compiledFxmlClass = Files.readAllBytes(paths.compiled("MainViewBase"));
        Files.writeString(paths.source("App.java"), """
            package test;

            public class App {
                public String value() {
                    return "changed";
                }
            }
        """);

        BuildResult javaEdit = build(projectDir, "classes");
        assertOutcome(javaEdit, ":processFxml", TaskOutcome.UP_TO_DATE);
        assertOutcome(javaEdit, ":compileJava", TaskOutcome.SUCCESS);
        assertArrayEquals(compiledFxmlClass, Files.readAllBytes(paths.compiled("MainViewBase")));

        Files.delete(paths.source("MainView.java"));
        Files.delete(paths.source("MainView.fxmlx"));
        BuildResult finalDeletion = build(projectDir, "classes");
        assertExecuted(finalDeletion);
        assertConfigurationCacheReused(finalDeletion);
        assertMissingOutputs(paths, "MainView");
    }

    private static void assertExecuted(BuildResult result) {
        assertOutcome(result, ":processFxml", TaskOutcome.SUCCESS);
        assertOutcome(result, ":compileJava", TaskOutcome.SUCCESS);
    }

    private static long countRegularFiles(Path directory) throws IOException {
        try (var files = Files.walk(directory)) {
            return files.filter(Files::isRegularFile).count();
        }
    }

    private static String compileJavaCacheKey(BuildResult result) {
        var pattern = Pattern.compile("Build cache key for task ':compileJava' is ([0-9a-f]+)");
        var matcher = pattern.matcher(result.getOutput());
        assertTrue(matcher.find(), "No compileJava cache key in build output");
        return matcher.group(1);
    }

    private static void assertConfigurationCacheReused(BuildResult result) {
        assertTrue(
            result.getOutput().contains("Configuration cache entry reused."),
            "Configuration cache was not reused");
    }

    private static void assertOutputs(Paths paths, String viewName) {
        assertAll(
            () -> assertTrue(Files.isRegularFile(paths.generated(viewName + "Base"))),
            () -> assertTrue(Files.isRegularFile(paths.descriptor(viewName + "Base"))),
            () -> assertTrue(Files.isRegularFile(paths.compiled(viewName))),
            () -> assertTrue(Files.isRegularFile(paths.compiled(viewName + "Base"))));
    }

    private static void assertMissingOutputs(Paths paths, String viewName) {
        assertAll(
            () -> assertFalse(Files.exists(paths.generated(viewName + "Base"))),
            () -> assertFalse(Files.exists(paths.descriptor(viewName + "Base"))),
            () -> assertFalse(Files.exists(paths.compiled(viewName))),
            () -> assertFalse(Files.exists(paths.compiled(viewName + "Base"))));
    }

    private record Paths(Path projectDir) {

        Path source(String name) {
            return projectDir.resolve("src/main/java/test").resolve(name);
        }

        Path generated(String name) {
            return projectDir.resolve("build/generated/sources/fxml/java/main/test").resolve(name + ".java");
        }

        Path descriptor(String name) {
            return projectDir.resolve("build/fxml/default/main/test").resolve(name + ".fxmd");
        }

        Path compiled(String name) {
            return projectDir.resolve("build/classes/java/main/test").resolve(name + ".class");
        }
    }
}
