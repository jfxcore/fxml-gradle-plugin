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
import java.util.Comparator;
import java.util.HashMap;
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

    public void copyClassFilesToCache(SourceSet sourceSet, List<File> classFiles) throws IOException {
        Path cacheBaseDir = project.getBuildDir().toPath()
            .resolve("fxml")
            .resolve(sourceSet.getName());

        Path classesDir = sourceSet.getJava().getClassesDirectory().get().getAsFile().toPath();

        if (Files.exists(cacheBaseDir)) {
            try (var stream = Files.walk(cacheBaseDir)) {
                stream.map(Path::toFile).sorted(Comparator.reverseOrder()).forEach(File::delete);
            }
        }

        List<String> fileNames = new ArrayList<>();

        try {
            for (File classFile : classFiles) {
                fileNames.add(classFile.toString());

                Path sourceClassFile = classFile.toPath();
                Path cacheFile = cacheBaseDir.resolve(classesDir.relativize(sourceClassFile));
                Path cacheDir = cacheFile.getParent();

                Files.createDirectories(cacheDir);
                Files.copy(sourceClassFile, cacheFile, StandardCopyOption.REPLACE_EXISTING);

                copyNestedClassFiles(sourceClassFile, cacheDir, fileNames);
            }
        } finally {
            if (fileNames.size() > 0) {
                project.getLogger().info(
                    "Copying compiled FXML class files to cache:\n  " +
                    String.join(System.lineSeparator() + "  ", fileNames));
            }
        }
    }

    public List<File> restoreClassFilesFromCache(SourceSet sourceSet, List<File> classFiles) throws IOException {
        Path cacheBaseDir = project.getBuildDir().toPath()
            .resolve("fxml")
            .resolve(sourceSet.getName());

        Path classesDir = sourceSet.getJava().getClassesDirectory().get().getAsFile().toPath();
        List<File> missingFiles = new ArrayList<>();
        List<String> fileNames = new ArrayList<>();

        try {
            for (File classFile : classFiles) {
                Path targetClassFile = classFile.toPath();
                Path relJavaFile = classesDir.relativize(targetClassFile);
                String fileNameWithoutExt = getFileNameWithoutExtension(relJavaFile);
                Path relJavaFileDir = relJavaFile.getParent();
                Path cacheDir = cacheBaseDir.resolve(relJavaFileDir);
                Path cacheFile = cacheDir.resolve(fileNameWithoutExt + ".class");

                if (Files.exists(cacheFile)) {
                    if (copyIfMismatch(cacheFile, targetClassFile)) {
                        fileNames.add(targetClassFile.toString());
                    }
                } else {
                    missingFiles.add(classFile);
                }

                copyNestedClassFiles(cacheFile, targetClassFile.getParent(), fileNames);
            }
        } finally {
            if (fileNames.size() > 0) {
                project.getLogger().info(
                    "Restoring compiled FXML class files from cache:\n  " +
                    String.join(System.lineSeparator() + "  ", fileNames));
            }
        }

        return missingFiles;
    }

    private void copyNestedClassFiles(Path classFile, Path targetDir, List<String> outCopiedFiles) throws IOException {
        Path classFileDir = classFile.getParent();
        if (!Files.exists(classFileDir)) {
            return;
        }

        String nestedClassFilePattern = getFileNameWithoutExtension(classFile) + "$";
        Predicate<Path> filter = f -> Files.exists(f) && Files.isRegularFile(f)
            && f.getFileName().toString().startsWith(nestedClassFilePattern);

        try (var stream = Files.walk(classFileDir, 1)) {
            for (var file : stream.filter(filter).toList()) {
                Path targetFile = targetDir.resolve(file.getFileName());

                if (copyIfMismatch(file, targetFile)) {
                    outCopiedFiles.add(file.toString());
                }
            }
        }
    }

    private boolean copyIfMismatch(Path source, Path destination) throws IOException {
        if (Files.exists(source) || !Files.exists(destination) || Files.mismatch(source, destination) >= 0) {
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            return true;
        }

        return false;
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
