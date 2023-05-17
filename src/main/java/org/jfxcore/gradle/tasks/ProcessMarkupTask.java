// Copyright (c) 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.gradle.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import org.jfxcore.gradle.compiler.CompilerService;
import org.jfxcore.gradle.PathHelper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;

public abstract class ProcessMarkupTask extends DefaultTask {

    public static final String NAME = "processMarkup";

    @Internal
    public abstract Property<CompilerService> getCompilerService();

    @TaskAction
    public void process() {
        Project project = getProject();
        PathHelper pathHelper = new PathHelper(project);
        CompilerService compilerService = getCompilerService().get();

        try {
            // Invoke the FXML parse and source generation stages for every source set.
            // This will generate .java source files that are placed in the generated sources directory.
            for (SourceSet sourceSet : pathHelper.getSourceSets()) {
                var compiler = compilerService.newCompiler(sourceSet, getLogger());

                for (File sourceDir : sourceSet.getAllSource().getSrcDirs()) {
                    compiler.parseFiles(sourceDir);
                }

                Path genSrcDir = pathHelper.getGeneratedSourcesDir(sourceSet).toPath();
                compiler.generateSources(genSrcDir.toFile());

                // Delete all .class files that may have been created by a previous compiler run.
                // This is necessary because the FXML compiler needs a 'clean slate' to work with.
                Predicate<Path> fileFilter = path -> path.toString().toLowerCase().endsWith(".java");
                for (Path file : pathHelper.enumerateFiles(genSrcDir, fileFilter)) {
                    String fileName = pathHelper.getFileNameWithoutExtension(file.toFile()) + ".class";
                    Path relFile = genSrcDir.relativize(file).getParent().resolve(fileName);
                    Path classesDir = sourceSet.getJava().getClassesDirectory().get().getAsFile().toPath();
                    Path classFile = classesDir.resolve(relFile);

                    if (Files.exists(classFile)) {
                        try {
                            Files.delete(classFile);
                        } catch (IOException ex) {
                            throw new GradleException("Cannot delete " + classFile, ex);
                        }
                    }
                }
            }
        } catch (GradleException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            if (compilerService.getExceptionHelper().isMarkupException(ex)) {
                project.getLogger().error(compilerService.getExceptionHelper().format(ex));
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
