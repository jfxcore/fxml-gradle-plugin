// Copyright (c) 2023, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.gradle.compiler;

import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public abstract class CompilerService implements BuildService<BuildServiceParameters.None>, AutoCloseable {

    public static final String NAME = "org.jfxcore.gradle.compiler.CompilerService";

    private final Map<UUID, Compiler> compilers = new HashMap<>();

    public static void register(Project project) {
        project.getGradle()
            .getSharedServices()
            .registerIfAbsent(CompilerService.class.getName(), CompilerService.class, spec -> {});
    }

    @Override
    public synchronized final void close() {
        // Make a copy of the compiler list because it will be modified by calling 'close'.
        for (Compiler compiler : List.copyOf(compilers.values())) {
            compiler.close();
        }
    }

    public final Compiler newCompiler(UUID id, FileCollection searchPath, File classesDir,
                                      File generatedSourcesDir, Logger logger) {
        // The getFiles() call must be outside of the synchronized block to prevent a potential deadlock,
        // as the method call will block until the files are resolved.
        Set<File> searchPathFiles = searchPath.getFiles();

        synchronized (this) {
            Compiler existingCompiler = compilers.get(id);
            if (existingCompiler != null) {
                existingCompiler.close();
            }

            try {
                Compiler compiler = new Compiler(classesDir, generatedSourcesDir, searchPathFiles, logger) {
                    @Override
                    public void close() {
                        super.close();

                        synchronized (CompilerService.this) {
                            compilers.remove(id);
                        }
                    }
                };

                compilers.put(id, compiler);
                return compiler;
            } catch (ReflectiveOperationException ex) {
                ExceptionHelper.throwUnchecked(ex);
                throw new AssertionError();
            }
        }
    }

    public synchronized final Compiler getCompiler(UUID id) {
        return compilers.get(id);
    }
}
