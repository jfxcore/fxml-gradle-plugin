// Copyright (c) 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.gradle.tasks;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import org.jfxcore.gradle.PathHelper;
import org.jfxcore.gradle.compiler.CompilerService;
import org.jfxcore.gradle.compiler.ExceptionHelper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;

public abstract class ProcessMarkupTask extends MarkupTask {

    public static final String VERB = "process";

    @TaskAction
    public void process() {
        Project project = getProject();
        SourceSet sourceSet = getSourceSet().get();

        ExceptionHelper.run(project, sourceSet, () -> {
            // Invoke the FXML parse and source generation stages for the source set.
            // This will generate .java source files that are placed in the generated sources directory.
            var compiler = CompilerService.get(project).getCompiler(sourceSet);
            if (compiler == null) {
                throw new GradleException(String.format(":%s cannot be run in isolation", getName()));
            }

            compiler.processFiles();

            PathHelper pathHelper = new PathHelper(project);
            File genSrcDir = pathHelper.getGeneratedSourcesDir(sourceSet);

            // Delete all .class files that may have been created by a previous compiler run.
            // This is necessary because the FXML compiler needs a 'clean slate' to work with.
            Predicate<Path> fileFilter = path -> path.toString().toLowerCase().endsWith(".java");
            for (Path file : pathHelper.enumerateFiles(genSrcDir.toPath(), fileFilter)) {
                String fileName = pathHelper.getFileNameWithoutExtension(file.toFile()) + ".class";
                Path relFile = genSrcDir.toPath().relativize(file).getParent().resolve(fileName);
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
        });
    }

}
