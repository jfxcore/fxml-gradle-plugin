// Copyright (c) 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.gradle.compiler;

import org.gradle.api.logging.Logger;
import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Set;

public class Compiler {

    public static final String COMPILER_NAME = "org.jfxcore.compiler.Compiler";
    private static final String LOGGER_NAME = "org.jfxcore.compiler.Logger";

    private final Object compilerInstance;
    private final Method parseFilesMethod;
    private final Method generateSourcesMethod;
    private final Method compileFilesMethod;

    public Compiler(Logger logger, Set<File> classpath, ClassLoader classLoader) throws Exception {
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

}
