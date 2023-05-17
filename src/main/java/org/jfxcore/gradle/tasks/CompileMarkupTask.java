// Copyright (c) 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.gradle.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import org.jfxcore.gradle.compiler.CompilerService;
import org.jfxcore.gradle.PathHelper;

public abstract class CompileMarkupTask extends DefaultTask {

    public static final String NAME = "compileMarkup";

    @Internal
    public abstract Property<CompilerService> getCompilerService();

    @TaskAction
    public void compile() {
        CompilerService compilerService = getCompilerService().get();

        try {
            for (SourceSet sourceSet : new PathHelper(getProject()).getSourceSets()) {
                var compiler = compilerService.getCompiler(sourceSet);
                if (compiler == null) {
                    throw new GradleException(
                        String.format(":%s cannot be run in isolation, please run :%s first",
                            NAME, ProcessMarkupTask.NAME));
                }

                compiler.compileFiles();
            }
        } catch (GradleException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            if (compilerService.getExceptionHelper().isMarkupException(ex)) {
                getProject().getLogger().error(compilerService.getExceptionHelper().format(ex));
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
