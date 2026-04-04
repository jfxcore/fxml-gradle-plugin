// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.gradle;

import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.process.CommandLineArgumentProvider;
import java.util.List;

final class CompilerArgumentsProvider implements CommandLineArgumentProvider {

    enum Target {
        JAVA, KOTLIN
    }

    private static final String INTERMEDIATE_DIR_OPT = "org.jfxcore.markup.processor.intermediateBuildDir";
    private static final String SOURCE_DIRS_OPT = "org.jfxcore.markup.processor.sourceDirs";
    private static final String SEARCH_PATH_OPT = "org.jfxcore.markup.processor.searchPath";

    private final Target target;
    private final FileCollection sourceDirs;
    private final FileCollection searchPath;
    private final DirectoryProperty intermediateBuildDir;

    CompilerArgumentsProvider(
            Target target,
            ObjectFactory objects,
            FileCollection sourceDirs,
            FileCollection searchPath,
            Provider<Directory> intermediateBuildDir) {
        this.target = target;
        this.sourceDirs = sourceDirs;
        this.searchPath = searchPath;
        this.intermediateBuildDir = objects.directoryProperty();
        this.intermediateBuildDir.set(intermediateBuildDir);
    }

    @Classpath
    public FileCollection getSearchPath() {
        return searchPath;
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileCollection getSourceDirs() {
        return sourceDirs;
    }

    @OutputDirectory
    public DirectoryProperty getIntermediateBuildDir() {
        return intermediateBuildDir;
    }

    @Override
    public Iterable<String> asArguments() {
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
}
