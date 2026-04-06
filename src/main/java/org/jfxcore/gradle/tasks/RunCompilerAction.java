// Copyright (c) 2025, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.gradle.tasks;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;
import org.jfxcore.gradle.PathHelper;
import org.jfxcore.gradle.compiler.CompilationUnitDescriptor;
import org.jfxcore.gradle.compiler.ExceptionHelper;
import org.jfxcore.gradle.compiler.MarkupCompiler;
import javax.inject.Inject;
import java.io.File;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public abstract class RunCompilerAction implements Action<Task> {

    private final FileCollection searchPath;
    private final File intermediateBuildDir;
    private final File classesDir;
    private final Logger logger;

    @Inject
    public RunCompilerAction(
            FileCollection searchPath,
            File intermediateBuildDir,
            File classesDir,
            Logger logger) {
        this.searchPath = searchPath;
        this.intermediateBuildDir = intermediateBuildDir;
        this.classesDir = classesDir;
        this.logger = logger;
    }

    @Override
    public void execute(Task task) {
        ExceptionHelper exceptionHelper = null;
        Path classesPath = classesDir.toPath();
        Path descriptorsPath = intermediateBuildDir.toPath();

        try (var compiler = new MarkupCompiler(searchPath.getFiles(), logger)) {
            exceptionHelper = compiler.getExceptionHelper();
            Set<CompilationUnitDescriptor> compilationUnits = new HashSet<>();

            for (File descriptorFile : PathHelper.getDescriptorFiles(intermediateBuildDir)) {
                Path relDescPath = descriptorsPath.relativize(descriptorFile.toPath());
                String fileName = PathHelper.getFileNameWithoutExtension(relDescPath);
                Path relClassFile = relDescPath.getParent().resolve(fileName + ".class");
                File classFile = classesPath.resolve(relClassFile).toFile();

                if (!classFile.exists() || !compiler.isCompiledFile(classFile)) {
                    compilationUnits.add(compiler.loadDescriptor(descriptorFile));
                }
            }

            compiler.compile(compilationUnits);
        } catch (Throwable ex) {
            if (exceptionHelper != null) {
                ExceptionHelper.handleException(exceptionHelper, ex, logger);
            }

            throw new GradleException("Internal compiler error", ex);
        }
    }
}
