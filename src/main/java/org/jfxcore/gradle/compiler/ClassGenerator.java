// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.gradle.compiler;

import org.gradle.api.logging.Logger;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class ClassGenerator extends AbstractCompiler {

    private static final String CLASS_NAME = "org.jfxcore.compiler.ClassGenerator";

    private final Object instance;
    private final Method addFileSourceMethod;
    private final Method processMethod;

    public ClassGenerator(Set<File> searchPath, Logger logger) throws ReflectiveOperationException {
        super(CLASS_NAME, searchPath, logger);

        instance = Class.forName(CLASS_NAME, true, getClassLoader())
            .getConstructor(Set.class, getCompilerLoggerClass())
            .newInstance(searchPath.stream().map(File::toPath).collect(Collectors.toUnmodifiableSet()), newCompilerLogger());

        addFileSourceMethod = instance.getClass().getMethod("addFileSource", Path.class, Path.class);
        processMethod = instance.getClass().getMethod("process");
    }

    public void addFileSources(Map<File, List<File>> markupFilesPerSourceDirectory) {
        for (var entry : markupFilesPerSourceDirectory.entrySet()) {
            File sourceDir = entry.getKey();
            List<File> sourceFiles = entry.getValue();

            for (File sourceFile : sourceFiles) {
                addFileSource(sourceDir, sourceFile);
            }
        }
    }

    private void addFileSource(File sourceDir, File sourceFile) {
        try {
            addFileSourceMethod.invoke(instance, sourceDir.toPath(), sourceFile.toPath());
        } catch (Throwable ex) {
            ExceptionHelper.throwUnchecked(ex.getCause());
        }
    }

    public List<CompilationUnit> process() {
        try {
            @SuppressWarnings("unchecked")
            List<Object> result = (List<Object>) processMethod.invoke(instance);
            return result.stream().map(CompilationUnit::new).toList();
        } catch (Throwable ex) {
            ExceptionHelper.throwUnchecked(ex.getCause());
            return List.of();
        }
    }
}
