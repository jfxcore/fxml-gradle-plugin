// Copyright (c) 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.gradle;

import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class PathHelper {

    private final Project project;

    public PathHelper(Project project) {
        this.project = project;
    }

    public File getGeneratedSourcesDir(SourceSet sourceSet) {
        return project.getBuildDir().toPath()
            .resolve("generated/sources/fxml/java")
            .resolve(sourceSet.getName()).toFile();
    }

    public Set<SourceSet> getSourceSets() {
        return project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets();
    }

    public Iterable<Path> enumerateFiles(Path basePath, Predicate<Path> filter) throws IOException {
        if (Files.isDirectory(basePath)) {
            try (Stream<Path> stream = Files.walk(basePath)) {
                Iterator<Path> it = stream.filter(p -> Files.isRegularFile(p) && filter.test(p)).toList().iterator();
                return () -> it;
            }
        }

        return Collections::emptyIterator;
    }

    public String getFileNameWithoutExtension(File file) {
        String name = file.getName();
        int lastIdx = name.lastIndexOf('.');
        return name.substring(0, lastIdx < 0 ? name.length() : lastIdx);
    }

}
