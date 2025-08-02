// Copyright (c) 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.gradle;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.Property;
import org.gradle.api.services.ServiceReference;
import org.jfxcore.gradle.compiler.Compiler;
import org.jfxcore.gradle.compiler.CompilerService;
import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

abstract class RunCompilerAction implements Action<Task> {

    @ServiceReference(CompilerService.NAME)
    abstract Property<CompilerService> getCompilerService();

    private final FileCollection searchPath;
    private final FileCollection srcDirs;
    private final File classesDir;
    private final File genSrcDir;
    private final Logger logger;

    @Inject
    public RunCompilerAction(
            FileCollection searchPath,
            FileCollection srcDirs,
            File classesDir,
            File genSrcDir,
            Logger logger) {
        this.searchPath = searchPath;
        this.classesDir = classesDir;
        this.srcDirs = srcDirs;
        this.genSrcDir = genSrcDir;
        this.logger = logger;
    }

    @Override
    public void execute(Task task) {
        runCompiler(searchPath, classesDir, srcDirs.getFiles(), genSrcDir, getCompilerService().get(), logger);
    }

    private void runCompiler(FileCollection searchPath, File classesDir, Set<File> srcDirs,
                             File genSrcDir, CompilerService compilerService, Logger logger) {
        Compiler compiler = null;

        try {
            compiler = compilerService.getCompiler(searchPath);

            if (compiler != null) {
                // If we have a compiler at this point, then ProcessFxmlTask has run before.
                // This means that all of our FXML class files are uncompiled, and need to be
                // compiled by the FXML compiler.
                compiler.compileFiles();
            } else {
                // If we don't have a compiler, ProcessFxmlTask was skipped. We can't be sure
                // that compileJava didn't re-compile our FXML class files, which would undo
                // the modifications that the FXML compiler has made to the files.
                // Luckily, we can detect whether a class file was compiled by the FXML compiler
                // since it includes a custom class file attribute. We invoke the compiler to
                // give us a list of all FXML class files that don't include the custom attribute,
                // and recompile only those files.
                var fxmlFilesPerSourceDirectory = PathHelper.getFxmlFilesPerSourceDirectory(srcDirs, genSrcDir);
                var recompilableFxmlFilesPerSourceDirectory = new HashMap<File, List<File>>();

                compiler = compilerService.newCompiler(searchPath, classesDir, genSrcDir, logger);
                compiler.addFiles(fxmlFilesPerSourceDirectory);

                for (var entry : compiler.getCompilationUnits().entrySet()) {
                    for (var compilationUnit :  entry.getValue()) {
                        if (compilationUnit.markupClassFile().exists()
                            && !compiler.isCompiledFile(compilationUnit.markupClassFile())) {
                            recompilableFxmlFilesPerSourceDirectory
                                .computeIfAbsent(entry.getKey(), key -> new ArrayList<>())
                                .add(compilationUnit.markupFile());
                        }
                    }
                }

                if (recompilableFxmlFilesPerSourceDirectory.size() > 0) {
                    compiler = compilerService.newCompiler(searchPath, classesDir, genSrcDir, logger);
                    compiler.addFiles(recompilableFxmlFilesPerSourceDirectory);
                    compiler.processFiles();
                    compiler.compileFiles();
                }
            }
        } catch (Throwable ex) {
            // If the FXML compiler fails, we need to delete all generated files.
            // This ensures that ProcessFxmlTask is no longer up-to-date, and it will
            // regenerate the files on the next build, causing the FXML compiler to run
            // once again.
            List<File> generatedFiles = compiler != null ?
                compiler.getCompilationUnits().getAllGeneratedFiles() : List.of();

            for (File file : generatedFiles) {
                if (file.exists()) {
                    try {
                        Files.delete(file.toPath());
                    } catch (IOException ex2) {
                        ex2.addSuppressed(ex);
                        throw new GradleException("Cannot delete " + file, ex2);
                    }
                }
            }

            if (compiler != null) {
                compiler.getExceptionHelper().handleException(ex, logger);
            }

            throw new GradleException("Internal compiler error", ex);
        } finally {
            if (compiler != null) {
                compiler.close();
            }
        }
    }
}
