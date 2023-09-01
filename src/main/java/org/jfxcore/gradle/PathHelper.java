// Copyright (c) 2023, JFXcore. All rights reserved.
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
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class PathHelper {

    private static final String[] FXML_EXTENSIONS = new String[] { ".fxml", ".fxmlx" };

    private final Project project;

    public PathHelper(Project project) {
        this.project = project;
    }

    public File getGeneratedSourcesDir(SourceSet sourceSet) {
        return project.getBuildDir().toPath()
            .resolve("generated/sources/fxml/java")
            .resolve(sourceSet.getName())
            .toFile();
    }

    public Set<SourceSet> getSourceSets() {
        return project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets();
    }

    public Map<File, List<File>> getMarkupFilesPerSourceDirectory(SourceSet sourceSet) {
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

    public void copyClassFilesToTempDirectory(SourceSet sourceSet, List<File> generatedJavaFiles) throws IOException {
        Path tempDir = project.getBuildDir().toPath()
            .resolve("tmp")
            .resolve("fxml")
            .resolve(sourceSet.getName());

        Path classesDir = sourceSet.getJava().getClassesDirectory().get().getAsFile().toPath();

        if (Files.exists(tempDir)) {
            try (var stream = Files.walk(tempDir)) {
                stream.map(Path::toFile).sorted(Comparator.reverseOrder()).forEach(File::delete);
            }
        }

        for (File classFile : getOutputClassFiles(sourceSet, generatedJavaFiles)) {
            Path tempFile = tempDir.resolve(classesDir.relativize(classFile.toPath()));
            Files.createDirectories(tempFile.getParent());
            Files.copy(classFile.toPath(), tempFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public void restoreClassFilesFromTempDirectory(SourceSet sourceSet, List<File> generatedJavaFiles) {
        Path tempDir = project.getBuildDir().toPath()
            .resolve("tmp")
            .resolve("fxml")
            .resolve(sourceSet.getName());

        Path classesDir = sourceSet.getJava().getClassesDirectory().get().getAsFile().toPath();
        Path genSrcDir = getGeneratedSourcesDir(sourceSet).toPath();

        for (File genJavaFile : generatedJavaFiles) {
            Path relJavaFile = genSrcDir.relativize(genJavaFile.toPath());
            String fileName = getFileNameWithoutExtension(relJavaFile) + ".class";
            Path relJavaFileDir = relJavaFile.getParent();
            Path tempFile = tempDir.resolve(relJavaFileDir).resolve(fileName);

            if (Files.exists(tempFile)) {
                Path targetFile = classesDir.resolve(relJavaFileDir).resolve(fileName);

                try {
                    if (Files.mismatch(tempFile, targetFile) >= 0) {
                        project.getLogger().info("Restoring from cache: " + targetFile);
                        Files.copy(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException ex) {
                    project.getLogger().error(String.format("Cannot copy %s to %s", tempFile, targetFile));
                }
            }
        }
    }

    public List<File> getOutputClassFiles(SourceSet sourceSet, List<File> generatedJavaFiles) {
        Path genSrcDir = getGeneratedSourcesDir(sourceSet).toPath();
        List<File> classFiles = new ArrayList<>();

        for (File file : generatedJavaFiles) {
            String fileName = getFileNameWithoutExtension(file) + ".class";
            Path relFile = genSrcDir.relativize(file.toPath()).getParent().resolve(fileName);
            Path classesDir = sourceSet.getJava().getClassesDirectory().get().getAsFile().toPath();
            classFiles.add(classesDir.resolve(relFile).toFile());
        }

        return classFiles;
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
