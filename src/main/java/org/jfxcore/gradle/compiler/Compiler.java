// Copyright (c) 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.gradle.compiler;

import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class Compiler implements AutoCloseable {

    public static final String COMPILER_NAME = "org.jfxcore.compiler.Compiler";
    private static final String LOGGER_NAME = "org.jfxcore.compiler.Logger";

    private final Object compilerInstance;
    private final Method closeMethod;
    private final Method addFileMethod;
    private final Method processFilesMethod;
    private final Method compileFilesMethod;
    private final CompilerClassLoader classLoader;
    private final ExceptionHelper exceptionHelper;

    public Compiler(File generatedSourcesDir, Set<File> searchPath, Logger logger)
            throws ReflectiveOperationException {
        List<URL> urls = new ArrayList<>(searchPath.stream().map(file -> {
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
            (proxy, method, args) -> switch (method.getName()) {
                case "debug" -> { logger.debug((String) args[0]); yield null; }
                case "info" -> { logger.lifecycle((String) args[0]); yield null; }
                case "error" -> { logger.error((String) args[0]); yield null; }
                default -> method.invoke(proxy, args);
            });

        compilerInstance = Class.forName(COMPILER_NAME, true, classLoader)
            .getConstructor(Path.class, Set.class, compilerLoggerClass)
            .newInstance(generatedSourcesDir.toPath(), searchPath, compilerLogger);

        closeMethod = compilerInstance.getClass().getMethod("close");
        addFileMethod = compilerInstance.getClass().getMethod("addFile", Path.class, Path.class);
        processFilesMethod = compilerInstance.getClass().getMethod("processFiles");
        compileFilesMethod = compilerInstance.getClass().getMethod("compileFiles");
    }

    @Override
    public void close() {
        try {
            closeMethod.invoke(compilerInstance);
            classLoader.close();
        } catch (Throwable ex) {
            ExceptionHelper.throwUnchecked(ex.getCause());
        }
    }

    public ExceptionHelper getExceptionHelper() {
        return exceptionHelper;
    }

    private Path addFile(File sourceDir, File sourceFile) {
        try {
            return (Path)addFileMethod.invoke(compilerInstance, sourceDir.toPath(), sourceFile.toPath());
        } catch (Throwable ex) {
            ExceptionHelper.throwUnchecked(ex.getCause());
            return null;
        }
    }

    public List<File> addFiles(Map<File, List<File>> markupFilesPerSourceDirectory) {
        List<File> generatedJavaFiles = new ArrayList<>();

        for (var entry : markupFilesPerSourceDirectory.entrySet()) {
            File sourceDir = entry.getKey();
            List<File> sourceFiles = entry.getValue();

            for (File sourceFile : sourceFiles) {
                Path generatedFile = addFile(sourceDir, sourceFile);
                if (generatedFile != null) {
                    generatedJavaFiles.add(generatedFile.toFile());
                }
            }
        }

        return generatedJavaFiles;
    }

    public void processFiles() {
        try {
            processFilesMethod.invoke(compilerInstance);
        } catch (Throwable ex) {
            ExceptionHelper.throwUnchecked(ex.getCause());
        }
    }

    public void compileFiles() {
        try {
            compileFilesMethod.invoke(compilerInstance);
        } catch (Throwable ex) {
            ExceptionHelper.throwUnchecked(ex.getCause());
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
