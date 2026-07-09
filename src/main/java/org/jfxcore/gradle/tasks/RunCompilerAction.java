// Copyright (c) 2025, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.gradle.tasks;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.jfxcore.compiler.runner.CompilationUnitDescriptorWrapper;
import org.jfxcore.compiler.runner.MarkupCompilerRunner;
import org.jfxcore.compiler.runner.RunnerException;
import org.jfxcore.gradle.PathHelper;
import javax.inject.Inject;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class RunCompilerAction implements Action<Task> {

    private final FileCollection searchPath;
    private final List<Provider<Directory>> intermediateBuildDirs;
    private final File classesDir;

    @Inject
    public RunCompilerAction(
            FileCollection searchPath,
            List<Provider<Directory>> intermediateBuildDirs,
            File classesDir) {
        this.searchPath = searchPath;
        this.intermediateBuildDirs = intermediateBuildDirs;
        this.classesDir = classesDir;
    }

    @Override
    public void execute(Task task) {
        Set<Path> searchPathSet = searchPath.getFiles().stream().map(File::toPath).collect(Collectors.toSet());
        Path classesPath = classesDir.toPath();

        try (var compiler = new MarkupCompilerRunner(searchPathSet, new GradleLoggerAdapter(task.getLogger()))) {
            Set<CompilationUnitDescriptorWrapper> compilationUnits = new HashSet<>();

            for (File intermediateBuildDir : intermediateBuildDirs.stream().map(p -> p.get().getAsFile()).toList()) {
                for (Path descriptorFile : PathHelper.getDescriptorFiles(intermediateBuildDir)) {
                    Path relDescPath = intermediateBuildDir.toPath().relativize(descriptorFile);
                    String fileName = PathHelper.getFileNameWithoutExtension(relDescPath);
                    Path relClassFile = relDescPath.getParent().resolve(fileName + ".class");
                    Path classFile = classesPath.resolve(relClassFile);

                    if (!Files.exists(classFile) || !compiler.isCompiledFile(classFile)) {
                        compilationUnits.add(compiler.loadDescriptor(descriptorFile));
                    }
                }
            }

            compiler.compile(compilationUnits);
        } catch (RunnerException ex) {
            throw new GradleException(ex.getMessage());
        } catch (Throwable ex) {
            throw new GradleException("Internal compiler error", ex);
        }
    }
}
