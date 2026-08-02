// Copyright (c) 2023, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.gradle;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class PathHelper {

    private static final String[] FXMD_EXTENSION = new String[] { ".fxmd" };

    private PathHelper() {}

    public static Provider<Directory> getGeneratedSourcesDirectory(Project project, SourceSet sourceSet) {
        return project.getLayout().getBuildDirectory()
            .dir("generated/sources/fxml/java/" + sourceSet.getName());
    }

    public static List<Path> getDescriptorFiles(File genSrcDir) {
        if (!genSrcDir.isDirectory()) {
            return List.of();
        }

        try (Stream<Path> stream = Files.walk(genSrcDir.toPath())) {
            return stream
                .filter(Files::isRegularFile)
                .filter(path -> fileFilter(path, FXMD_EXTENSION))
                .toList();
        } catch (IOException ex) {
            throw new GradleException(
                String.format("Compilation failed with %s: %s", ex.getClass().getName(), ex.getMessage()));
        }
    }

    public static String getFileNameWithoutExtension(Path file) {
        String name = file.getName(file.getNameCount() - 1).toString();
        int lastIdx = name.lastIndexOf('.');
        return name.substring(0, lastIdx < 0 ? name.length() : lastIdx);
    }

    private static boolean fileFilter(Path path, String[] extensions) {
        String file = path.toString().toLowerCase(Locale.ROOT);

        for (String ext : extensions) {
            if (file.endsWith(ext)) {
                return true;
            }
        }

        return false;
    }
}
