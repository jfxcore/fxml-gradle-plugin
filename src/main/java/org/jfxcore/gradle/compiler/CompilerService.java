// Copyright (c) 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.gradle.compiler;

import org.gradle.api.Project;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.api.tasks.SourceSet;
import java.io.File;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class CompilerService implements BuildService<BuildServiceParameters.None>, AutoCloseable {

    private final Map<SourceSet, Compiler> compilers = new IdentityHashMap<>();

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

    public synchronized final Compiler newCompiler(Project project, SourceSet sourceSet, File generatedSourcesDir) {
        Compiler existingCompiler = compilers.get(sourceSet);
        if (existingCompiler != null) {
            existingCompiler.close();
        }

        Set<File> searchPath = new HashSet<>();
        searchPath.addAll(sourceSet.getOutput().getFiles());
        searchPath.addAll(sourceSet.getCompileClasspath().getFiles());

        try {
            Compiler compiler = new Compiler(project, sourceSet, generatedSourcesDir, searchPath) {
                @Override
                public void close() {
                    super.close();

                    synchronized (CompilerService.this) {
                        compilers.remove(sourceSet);
                    }
                }
            };

            compilers.put(sourceSet, compiler);
            return compiler;
        } catch (ReflectiveOperationException ex) {
            ExceptionHelper.throwUnchecked(ex);
            throw new AssertionError();
        }
    }

    public synchronized final Compiler getCompiler(SourceSet sourceSet) {
        return compilers.get(sourceSet);
    }

}
