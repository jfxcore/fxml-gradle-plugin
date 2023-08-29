// Copyright (c) 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.gradle.compiler;

import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.api.tasks.SourceSet;
import java.io.File;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public abstract class CompilerService implements BuildService<BuildServiceParameters.None>, AutoCloseable {

    private final Map<SourceSet, Compiler> compilers = new IdentityHashMap<>();

    public static void register(Project project) {
        project.getGradle()
            .getSharedServices()
            .registerIfAbsent(
                String.format("%s:%s", project.getPath(), CompilerService.class.getName()),
                CompilerService.class,
                spec -> {});
    }

    public static CompilerService get(Project project) {
        return (CompilerService)project.getGradle()
            .getSharedServices()
            .getRegistrations()
            .findByName(String.format("%s:%s", project.getPath(), CompilerService.class.getName()))
            .getService()
            .get();
    }

    @Override
    public final void close() throws Exception {
        for (Compiler compiler : compilers.values()) {
            compiler.close();
        }
    }

    public final synchronized Compiler newCompiler(SourceSet sourceSet, Logger logger) throws Exception {
        Set<File> classpath = new HashSet<>();
        classpath.addAll(sourceSet.getOutput().getFiles());
        classpath.addAll(sourceSet.getCompileClasspath().getFiles());

        Compiler instance = new Compiler(classpath, logger);
        compilers.put(sourceSet, instance);
        return instance;
    }

    public final synchronized Compiler getCompiler(SourceSet sourceSet) {
        return compilers.get(sourceSet);
    }

}
