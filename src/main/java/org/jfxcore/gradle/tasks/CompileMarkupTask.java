// Copyright (c) 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.gradle.tasks;

import org.gradle.api.GradleException;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import org.jfxcore.gradle.compiler.CompilerService;

public abstract class CompileMarkupTask extends MarkupTask {

    public static final String VERB = "compile";

    @TaskAction
    public void compile() {
        CompilerService compilerService = CompilerService.get(getProject());
        SourceSet sourceSet = getSourceSet().get();

        try {
            var compiler = compilerService.getCompiler(sourceSet);
            if (compiler == null) {
                throw new GradleException(String.format(
                    ":%s cannot be run in isolation, please run :%s first",
                    getName(), sourceSet.getTaskName(ProcessMarkupTask.VERB, TARGET)));
            }

            compiler.compileFiles();
        } catch (GradleException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            var exceptionHelper = compilerService.getCompiler(sourceSet).getExceptionHelper();
            if (exceptionHelper.isMarkupException(ex)) {
                getProject().getLogger().error(exceptionHelper.format(ex));
            } else {
                throw ex;
            }

            throw new GradleException("Compilation failed; see the compiler error output for details.");
        } catch (Throwable ex) {
            String message = ex.getMessage();
            throw new GradleException(
                message == null || message.isEmpty() ? "Internal compiler error" : message, ex);
        }
    }

}
