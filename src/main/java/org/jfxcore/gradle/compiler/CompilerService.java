// Copyright (c) 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.gradle.compiler;

import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.api.tasks.SourceSet;
import java.util.IdentityHashMap;
import java.util.Map;

public abstract class CompilerService implements BuildService<BuildServiceParameters.None>, AutoCloseable {

    private final Map<SourceSet, Compiler> compilers = new IdentityHashMap<>();

    public static CompilerService get(Project project) {
        return (CompilerService)project.getGradle()
            .getSharedServices()
            .getRegistrations()
            .findByName(CompilerService.class.getName())
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
        Compiler instance = new Compiler(sourceSet.getRuntimeClasspath().getFiles(), logger);
        compilers.put(sourceSet, instance);
        return instance;
    }

    public final synchronized Compiler getCompiler(SourceSet sourceSet) {
        return compilers.get(sourceSet);
    }

}
