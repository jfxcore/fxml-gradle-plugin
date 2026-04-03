// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.gradle.compiler;

import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

abstract class AbstractCompiler implements AutoCloseable {

    private static final String LOGGER_NAME = "org.jfxcore.compiler.diagnostic.Logger";

    private final Logger logger;
    private final CompilerClassLoader classLoader;
    private final ExceptionHelper exceptionHelper;

    AbstractCompiler(String implName, Set<File> searchPath, Logger logger) {
        this.logger = logger;

        List<URL> urls = searchPath.stream().map(file -> {
            try {
                return file.toURI().toURL();
            } catch (IOException e) {
                return null;
            }
        }).filter(Objects::nonNull).toList();

        classLoader = new CompilerClassLoader(urls.toArray(URL[]::new), getClass().getClassLoader());
        checkDependencies(implName, classLoader);

        exceptionHelper = new ExceptionHelper(classLoader);
    }

    @Override
    public final void close() {
        try {
            classLoader.close();
        } catch (Throwable ex) {
            ExceptionHelper.throwUnchecked(ex.getCause());
        }
    }

    public final ExceptionHelper getExceptionHelper() {
        return exceptionHelper;
    }

    final ClassLoader getClassLoader() {
        return classLoader;
    }

    final Class<?> getCompilerLoggerClass() throws ReflectiveOperationException {
        return Class.forName(LOGGER_NAME, true, classLoader);
    }

    final Object newCompilerLogger() throws ReflectiveOperationException {
        return Proxy.newProxyInstance(
            classLoader, new Class[] { getCompilerLoggerClass() },
            (proxy, method, args) -> switch (method.getName()) {
                case "fine" -> { logger.info((String) args[0]); yield null; }
                case "info" -> { logger.lifecycle((String) args[0]); yield null; }
                default -> method.invoke(proxy, args);
            });
    }

    static void checkDependencies(String implClass, ClassLoader classLoader) {
        try {
            Class.forName(implClass, true, classLoader);
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
