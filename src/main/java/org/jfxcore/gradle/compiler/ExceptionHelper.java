// Copyright (c) 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.gradle.compiler;

import java.io.File;

public final class ExceptionHelper {

    private static final String CLASS_NAME = "org.jfxcore.compiler.diagnostic.MarkupException";

    private final Class<?> markupExceptionClass;

    public ExceptionHelper(ClassLoader classLoader) {
        try {
            markupExceptionClass = Class.forName(CLASS_NAME, true, classLoader);
        } catch (ClassNotFoundException ex) {
            String message = "Class not found: " + ex.getMessage();
            throw new RuntimeException(message, ex);
        }
    }

    public boolean isMarkupException(RuntimeException ex) {
        return markupExceptionClass.isInstance(ex);
    }

    public String format(RuntimeException ex) {
        try {
            File sourceFile = (File)ex.getClass().getMethod("getSourceFile").invoke(ex);
            String message = (String)ex.getClass().getMethod("getMessageWithSourceInfo").invoke(ex);
            Object sourceInfo = ex.getClass().getMethod("getSourceInfo").invoke(ex);
            Object location = sourceInfo.getClass().getMethod("getStart").invoke(sourceInfo);
            int line = (int)location.getClass().getMethod("getLine").invoke(location);

            return String.format("%s:%s: %s", sourceFile != null ? sourceFile.toString() : "<null>", line + 1, message);
        } catch (ReflectiveOperationException ex2) {
            throwUnchecked(ex2);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static <E extends Throwable> void throwUnchecked(Throwable e) throws E {
        throw (E)e;
    }

}
