// Copyright (c) 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.gradle.compiler;

import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class Compiler implements AutoCloseable {

    public static final String COMPILER_NAME = "org.jfxcore.compiler.Compiler";
    private static final String LOGGER_NAME = "org.jfxcore.compiler.Logger";

    private final Object compilerInstance;
    private final Method parseFilesMethod;
    private final Method generateSourcesMethod;
    private final Method compileFilesMethod;
    private final CompilerClassLoader classLoader;
    private final ExceptionHelper exceptionHelper;

    public Compiler(Set<File> classpath, Logger logger) throws Exception {
        List<URL> urls = new ArrayList<>(classpath.stream().map(file -> {
            try {
                return new URL("file", null, file.getCanonicalPath());
            } catch (IOException e) {
                return null;
            }
        }).filter(Objects::nonNull).toList());

        classLoader = new CompilerClassLoader(urls.toArray(URL[]::new), getClass().getClassLoader());
        checkDependencies(classLoader);

        exceptionHelper = new ExceptionHelper(classLoader);

        Class<?> compilerLoggerClass = Class.forName(LOGGER_NAME, true, classLoader);

        Object compilerLogger = Proxy.newProxyInstance(
            compilerLoggerClass.getClassLoader(),
            new Class[] {compilerLoggerClass},
            new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args)
                        throws InvocationTargetException, IllegalAccessException {
                    switch (method.getName()) {
                        case "debug":
                            logger.debug((String)args[0]);
                            return null;

                        case "info":
                            logger.lifecycle((String)args[0]);
                            return null;

                        case "error":
                            logger.error((String)args[0]);
                            return null;
                    }

                    return method.invoke(proxy, args);
                }
            });

        compilerInstance = Class.forName(COMPILER_NAME, true, classLoader)
            .getConstructor(Set.class, compilerLoggerClass)
            .newInstance(classpath, compilerLogger);

        parseFilesMethod = compilerInstance.getClass().getMethod("parseFiles", File.class);
        generateSourcesMethod = compilerInstance.getClass().getMethod("generateSources", File.class);
        compileFilesMethod = compilerInstance.getClass().getMethod("compileFiles");
    }

    @Override
    public void close() throws IOException {
        classLoader.close();
    }

    public ExceptionHelper getExceptionHelper() {
        return exceptionHelper;
    }

    public void parseFiles(File sourceDir) throws Throwable {
        try {
            parseFilesMethod.invoke(compilerInstance, sourceDir);
        } catch (InvocationTargetException ex) {
            throw ex.getCause();
        }
    }

    public void generateSources(File generatedSourcesDir) throws Throwable {
        try {
            generateSourcesMethod.invoke(compilerInstance, generatedSourcesDir);
        } catch (InvocationTargetException ex) {
            throw ex.getCause();
        }
    }

    public void compileFiles() throws Throwable {
        try {
            compileFilesMethod.invoke(compilerInstance);
        } catch (InvocationTargetException ex) {
            throw ex.getCause();
        }
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
