// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.gradle.compiler;

import org.gradle.api.logging.Logger;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

public final class MarkupCompiler extends AbstractCompiler {

    private static final String CLASS_NAME = "org.jfxcore.compiler.MarkupCompiler";
    private static final String DESCRIPTOR_CLASS_NAME = "org.jfxcore.compiler.util.CompilationUnitDescriptor";

    private final Object instance;
    private final Method compileMethod;
    private final Method isCompiledFileMethod;
    private final Method loadDescriptorMethod;

    public MarkupCompiler(Set<File> searchPath, Logger logger) throws ReflectiveOperationException {
        super(CLASS_NAME, searchPath, logger);

        instance = Class.forName(CLASS_NAME, true, getClassLoader())
            .getConstructor(Set.class, getCompilerLoggerClass())
            .newInstance(searchPath.stream().map(File::toPath).collect(Collectors.toUnmodifiableSet()), newCompilerLogger());

        compileMethod = instance.getClass().getMethod("compile", Set.class);
        isCompiledFileMethod = instance.getClass().getMethod("isCompiledFile", Path.class);
        loadDescriptorMethod = Class.forName(DESCRIPTOR_CLASS_NAME, true, getClassLoader()).getMethod("readFrom", Path.class);
    }

    public CompilationUnitDescriptor loadDescriptor(File file) {
        try {
            return new CompilationUnitDescriptor(loadDescriptorMethod.invoke(null, file.toPath()));
        } catch (Throwable ex) {
            ExceptionHelper.throwUnchecked(ex.getCause());
            return null;
        }
    }

    public boolean isCompiledFile(File classFile) {
        try {
            return (boolean)isCompiledFileMethod.invoke(instance, classFile.toPath());
        } catch (Throwable ex) {
            ExceptionHelper.throwUnchecked(ex.getCause());
            return false;
        }
    }

    public void compile(Set<CompilationUnitDescriptor> descriptors) {
        try {
            Set<Object> targetDescriptors = descriptors.stream()
                .map(CompilationUnitDescriptor::getTarget)
                .collect(Collectors.toUnmodifiableSet());

            compileMethod.invoke(instance, targetDescriptors);
        } catch (Throwable ex) {
            ExceptionHelper.throwUnchecked(ex.getCause());
        }
    }
}
