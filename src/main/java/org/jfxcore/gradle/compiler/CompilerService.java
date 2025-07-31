// Copyright (c) 2023, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.gradle.compiler;

import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import java.io.File;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public abstract class CompilerService implements BuildService<BuildServiceParameters.None>, AutoCloseable {

    public static final String NAME = "org.jfxcore.gradle.compiler.CompilerService";

    private final Map<FileCollection, Compiler> compilers = new IdentityHashMap<>();

    public static void register(Project project) {
        project.getGradle()
            .getSharedServices()
            .registerIfAbsent(CompilerService.class.getName(), CompilerService.class, spec -> {});
    }

    public static CompilerService get(Project project) {
        return (CompilerService)project.getGradle()
            .getSharedServices()
            .getRegistrations()
            .findByName(CompilerService.class.getName())
            .getService()
            .get();
    }

    @Override
    public synchronized final void close() {
        // Make a copy of the compiler list because it will be modified by calling 'close'.
        for (Compiler compiler : List.copyOf(compilers.values())) {
            compiler.close();
        }
    }

    public synchronized final Compiler newCompiler(FileCollection searchPath, File classesDir,
                                                   File generatedSourcesDir, Logger logger) {
        Compiler existingCompiler = compilers.get(searchPath);
        if (existingCompiler != null) {
            existingCompiler.close();
        }

        try {
            Compiler compiler = new Compiler(classesDir, generatedSourcesDir, searchPath.getFiles(), logger) {
                @Override
                public void close() {
                    super.close();

                    synchronized (CompilerService.this) {
                        compilers.remove(searchPath);
                    }
                }
            };

            compilers.put(searchPath, compiler);
            return compiler;
        } catch (ReflectiveOperationException ex) {
            ExceptionHelper.throwUnchecked(ex);
            throw new AssertionError();
        }
    }

    public synchronized final Compiler getCompiler(FileCollection searchPath) {
        return compilers.get(searchPath);
    }

}
