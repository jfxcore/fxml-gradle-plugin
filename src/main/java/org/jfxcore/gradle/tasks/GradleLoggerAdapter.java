// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.gradle.tasks;

import org.gradle.api.logging.Logger;
import org.jfxcore.compiler.runner.RunnerLogger;

record GradleLoggerAdapter(Logger logger) implements RunnerLogger {

    @Override
    public void error(String message) {
        logger.error(message);
    }

    @Override
    public void info(String message) {
        logger.lifecycle(message);
    }

    @Override
    public void fine(String message) {
        logger.info(message);
    }
}
