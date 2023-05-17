// Copyright (c) 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.gradle.compiler;

import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.api.tasks.SourceSet;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public abstract class CompilerService implements BuildService<CompilerService.Params>, AutoCloseable {

    public interface Params extends BuildServiceParameters {
        SetProperty<File> getCompileClasspath();
    }

    private final Set<File> compileClasspath;
    private final CompilerClassLoader classLoader;
    private final ExceptionHelper exceptionHelper;
    private final Map<SourceSet, Compiler> compilers = new IdentityHashMap<>();

    public CompilerService() {
        compileClasspath = getParameters().getCompileClasspath().getOrElse(Collections.emptySet());

        List<URL> urls = new ArrayList<>(compileClasspath.stream().map(file -> {
            try {
                return new URL("file", null, file.getCanonicalPath());
            } catch (IOException e) {
                return null;
            }
        }).filter(Objects::nonNull).toList());

        classLoader = new CompilerClassLoader(urls.toArray(URL[]::new), getClass().getClassLoader());
        checkDependencies(classLoader);

        exceptionHelper = new ExceptionHelper(classLoader);
    }

    @Override
    public void close() throws Exception {
        classLoader.close();
    }

    public ExceptionHelper getExceptionHelper() {
        return exceptionHelper;
    }

    public Compiler newCompiler(SourceSet sourceSet, Logger logger) throws Exception {
        Compiler instance = new Compiler(logger, compileClasspath, classLoader);
        compilers.put(sourceSet, instance);
        return instance;
    }

    public Compiler getCompiler(SourceSet sourceSet) {
        return compilers.get(sourceSet);
    }

    private static void checkDependencies(ClassLoader classLoader) {
        try {
            Class.forName(Compiler.COMPILER_NAME, true, classLoader);
        } catch (ClassNotFoundException ex) {
            throw new GradleException("Compiler not found");
        }

        List<String> missingDeps = new ArrayList<>();

        try {
            Class.forName("javafx.beans.Observable", true, classLoader);
        } catch (ClassNotFoundException ex) {
            missingDeps.add("javafx.base");
        }

        try {
            Class.forName("javafx.geometry.Bounds", true, classLoader);
        } catch (ClassNotFoundException ex) {
            missingDeps.add("javafx.graphics");
        }

        if (!missingDeps.isEmpty()) {
            throw new GradleException("Missing module dependencies: " + String.join(", ", missingDeps));
        }
    }

}
