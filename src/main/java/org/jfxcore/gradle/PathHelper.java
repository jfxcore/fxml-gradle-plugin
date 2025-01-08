// Copyright (c) 2023, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.gradle;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class PathHelper {

    private static final String[] FXML_EXTENSIONS = new String[] { ".fxml", ".fxmlx" };

    private final Project project;

    public PathHelper(Project project) {
        this.project = project;
    }

    public File getGeneratedSourcesDir(SourceSet sourceSet) {
        return project.getLayout().getBuildDirectory().get().getAsFile().toPath()
            .resolve("generated/sources/fxml/java")
            .resolve(sourceSet.getName())
            .toFile();
    }

    public Set<SourceSet> getSourceSets() {
        return project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets();
    }

    public Map<File, List<File>> getFxmlFilesPerSourceDirectory(SourceSet sourceSet) {
        Map<File, List<File>> result = new HashMap<>();
        File genSrcDir = getGeneratedSourcesDir(sourceSet);

        for (File sourceDir : sourceSet.getAllSource().getSrcDirs()) {
            // If the current source directory is a generated sources directory, skip it.
            if (genSrcDir.equals(sourceDir)) {
                continue;
            }

            List<File> files = new ArrayList<>();
            Path sourcePath = sourceDir.toPath();

            try (Stream<Path> stream = Files.isDirectory(sourcePath) ? Files.walk(sourcePath) : Stream.empty()) {
                stream.filter(this::fileFilter).forEach(file -> files.add(file.toFile()));
            } catch (IOException ex) {
                throw new GradleException(
                    String.format("Compilation failed with %s: %s", ex.getClass().getName(), ex.getMessage()));
            }

            result.put(sourceDir, files);
        }

        return result;
    }

    public String getFileNameWithoutExtension(Path file) {
        String name = file.getName(file.getNameCount() - 1).toString();
        int lastIdx = name.lastIndexOf('.');
        return name.substring(0, lastIdx < 0 ? name.length() : lastIdx);
    }

    private boolean fileFilter(Path path) {
        String file = path.toString().toLowerCase(Locale.ROOT);

        for (String ext : FXML_EXTENSIONS) {
            if (file.endsWith(ext)) {
                return true;
            }
        }

        return false;
    }

}
