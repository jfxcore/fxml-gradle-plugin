// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.CRC32;
import java.util.zip.ZipFile;

import static org.jfxcore.gradle.TestProject.*;
import static org.junit.jupiter.api.Assertions.*;

class EmbeddedResourceFunctionalTest {

    @TempDir
    Path projectDir;

    @Test
    void materializesPackagesAndRemovesGeneratedResources() throws IOException {
        copyFixture("lifecycle", projectDir);
        String resourceName =  resourceName("MainView", "message.txt");
        Path source = projectDir.resolve("src/main/java/test/MainView.fxmlx");
        Path generatedResource = projectDir.resolve("build/generated/resources/fxml/main/test/" + resourceName);
        Path processedResource = projectDir.resolve("build/resources/main/test/" + resourceName);
        Path jar = projectDir.resolve("build/libs/lifecycle-functional-test.jar");

        Files.writeString(source, fxml("<?resource message.txt:generated resource?>"));

        BuildResult generated = build(projectDir, "jar");

        assertAll(
            () -> assertOutcome(generated, ":processFxml", TaskOutcome.SUCCESS),
            () -> assertOutcome(generated, ":processResources", TaskOutcome.SUCCESS),
            () -> assertEquals("generated resource", Files.readString(generatedResource)),
            () -> assertEquals("generated resource", Files.readString(processedResource)),
            () -> assertJarEntry(jar, "test/" + resourceName, "generated resource"));

        Files.writeString(source, fxml(""));

        BuildResult removed = build(projectDir, "jar");

        assertAll(
            () -> assertOutcome(removed, ":processFxml", TaskOutcome.SUCCESS),
            () -> assertOutcome(removed, ":processResources", TaskOutcome.SUCCESS),
            () -> assertFalse(Files.exists(generatedResource)),
            () -> assertFalse(Files.exists(processedResource)),
            () -> assertJarEntryMissing(jar, "test/" + resourceName));
    }

    private static String fxml(String resourceDeclaration) {
        return """
            <?import javafx.scene.layout.*?>
            %s

            <Pane xmlns="http://javafx.com/javafx"
                  xmlns:fx="http://jfxcore.org/fxml/2.0"
                  fx:subclass="test.MainView"/>
        """.formatted(resourceDeclaration);
    }

    private static void assertJarEntry(Path jar, String name, String expectedContent) throws IOException {
        try (var zip = new ZipFile(jar.toFile())) {
            var entry = zip.getEntry(name);
            assertNotNull(entry, () -> "JAR entry does not exist: " + name);
            assertEquals(expectedContent,
                new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static void assertJarEntryMissing(Path jar, String name) throws IOException {
        try (var zip = new ZipFile(jar.toFile())) {
            assertNull(zip.getEntry(name), () -> "Unexpected JAR entry: " + name);
        }
    }

    private String resourceName(String className, String logicalName) {
        var crc = new CRC32();
        crc.update(className.getBytes(StandardCharsets.UTF_8));
        crc.update(logicalName.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
        return className + "$" + Long.toHexString(crc.getValue()) + "$" + logicalName;
    }
}
