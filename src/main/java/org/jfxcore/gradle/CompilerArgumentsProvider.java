// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.gradle;

import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.process.CommandLineArgumentProvider;
import java.io.File;
import java.nio.file.Path;
import java.util.List;

final class CompilerArgumentsProvider implements CommandLineArgumentProvider {

    enum Target {
        JAVA, KOTLIN
    }

    private static final String INTERMEDIATE_DIR_OPT = "org.jfxcore.compiler.processor.intermediateBuildDir";
    private static final String SOURCE_DIRS_OPT = "org.jfxcore.compiler.processor.sourceDirs";
    private static final String SEARCH_PATH_OPT = "org.jfxcore.compiler.processor.searchPath";

    private final Target target;
    private final Property<Boolean> enabled;
    private final FileCollection sourceDirs;
    private final FileCollection searchPath;
    private final FileCollection emptyFiles;
    private final DirectoryProperty intermediateBuildDir;
    private final File projectDir;

    CompilerArgumentsProvider(
            Target target,
            ObjectFactory objects,
            Provider<Boolean> enabled,
            FileCollection sourceDirs,
            FileCollection searchPath,
            Provider<Directory> intermediateBuildDir,
            Directory projectDir) {
        this.target = target;
        this.enabled = objects.property(Boolean.class);
        this.enabled.set(enabled);
        this.sourceDirs = sourceDirs;
        this.searchPath = searchPath;
        this.emptyFiles = objects.fileCollection();
        this.intermediateBuildDir = objects.directoryProperty();
        this.intermediateBuildDir.set(intermediateBuildDir);
        this.projectDir = projectDir.getAsFile();
    }

    @Input
    public Property<Boolean> getEnabled() {
        return enabled;
    }

    @Classpath
    public FileCollection getSearchPath() {
        return enabled.getOrElse(false) ? searchPath : emptyFiles;
    }

    @Internal
    public FileCollection getSourceDirs() {
        return sourceDirs;
    }

    /**
     * Source roots are used to locate an annotated source file within the source-set layout. Their contents are
     * already tracked by the Java or Kotlin compilation task and must not become an additional recursive file input.
     * <p>
     * The returned strings are used only as Gradle input fingerprints; they are not passed to the annotation or
     * symbol processor. Paths inside the project are tagged with {@code project:} and stored relative to the project
     * directory so that the fingerprint is stable when the project is relocated. Paths outside the project are tagged
     * with {@code absolute:} and retain their absolute location because they cannot safely be treated as relocatable.
     * The prefixes are namespace tags that make the two representations unambiguous, Gradle does not interpret them.
     */
    @Input
    public List<String> getSourceDirLayout() {
        if (!enabled.getOrElse(false)) {
            return List.of();
        }

        Path projectPath = projectDir.toPath().toAbsolutePath().normalize();

        return sourceDirs.getFiles().stream()
            .map(File::toPath)
            .map(Path::toAbsolutePath)
            .map(Path::normalize)
            .map(sourcePath -> sourcePath.startsWith(projectPath)
                ? "project:" + toPortablePath(projectPath.relativize(sourcePath))
                : "absolute:" + toPortablePath(sourcePath))
            .toList();
    }

    @OutputDirectory
    public DirectoryProperty getIntermediateBuildDir() {
        return intermediateBuildDir;
    }

    @Override
    public Iterable<String> asArguments() {
        return enabled.getOrElse(false) ? getArguments() : List.of();
    }

    private Iterable<String> getArguments() {
        return target == Target.JAVA
            ? List.of(
                "-A" + SOURCE_DIRS_OPT + "=" + sourceDirs.getAsPath(),
                "-A" + SEARCH_PATH_OPT + "=" + searchPath.getAsPath(),
                "-A" + INTERMEDIATE_DIR_OPT + "=" + intermediateBuildDir.get().getAsFile().getAbsolutePath())
            : List.of(
                SOURCE_DIRS_OPT + "=" + sourceDirs.getAsPath(),
                SEARCH_PATH_OPT + "=" + searchPath.getAsPath(),
                INTERMEDIATE_DIR_OPT + "=" + intermediateBuildDir.get().getAsFile().getAbsolutePath());
    }

    private static String toPortablePath(Path path) {
        String result = path.toString().replace(File.separatorChar, '/');
        return result.isEmpty() ? "." : result;
    }
}
