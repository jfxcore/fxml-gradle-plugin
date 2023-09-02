// Copyright (c) 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.gradle.compiler;

import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import java.io.File;

public final class ExceptionHelper {

    private final Class<?> markupExceptionClass;
    private final Class<?> diagnosticClass;
    private final Class<?> errorCodeClass;

    public ExceptionHelper(ClassLoader classLoader) {
        try {
            markupExceptionClass = Class.forName("org.jfxcore.compiler.diagnostic.MarkupException", true, classLoader);
            diagnosticClass = Class.forName("org.jfxcore.compiler.diagnostic.Diagnostic", true, classLoader);
            errorCodeClass = Class.forName("org.jfxcore.compiler.diagnostic.ErrorCode", true, classLoader);
        } catch (ClassNotFoundException ex) {
            String message = "Class not found: " + ex.getMessage();
            throw new RuntimeException(message, ex);
        }
    }

    public boolean isMarkupException(RuntimeException ex) {
        return markupExceptionClass.isInstance(ex);
    }

    public boolean isInternalError(RuntimeException ex) {
        if (!isMarkupException(ex)) {
            return false;
        }

        try {
            Object diagnostic = ex.getClass().getMethod("getDiagnostic").invoke(ex);
            Object errorCode = diagnosticClass.getMethod("getCode").invoke(diagnostic);
            return (int)errorCodeClass.getMethod("ordinal").invoke(errorCode) == 0;
        } catch (ReflectiveOperationException ex2) {
            throwUnchecked(ex2);
            return false;
        }
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

    public void handleException(Throwable ex, Logger logger) {
        if (ex instanceof RuntimeException r) {
            if (isInternalError(r)) {
                logger.error(format(r));
                throw new GradleException("Internal compiler error; please clean and rebuild the project.");
            }

            if (isMarkupException(r)) {
                logger.error(format(r));
                throw new GradleException("Compilation failed; see the compiler error output for details.");
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static <E extends Throwable> void throwUnchecked(Throwable e) throws E {
        throw (E)e;
    }

}
