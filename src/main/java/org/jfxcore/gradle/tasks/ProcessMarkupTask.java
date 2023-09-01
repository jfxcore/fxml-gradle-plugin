// Copyright (c) 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.gradle.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.util.PatternSet;
import org.jfxcore.gradle.PathHelper;
import org.jfxcore.gradle.compiler.CompilerService;
import org.jfxcore.gradle.compiler.ExceptionHelper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public abstract class ProcessMarkupTask extends DefaultTask {

    public static final String VERB = "process";
    public static final String TARGET = "fxml";

    private static final PatternSet PATTERN_SET = new PatternSet().include("**/*.fxml", "**/*.fxmlx");

    private FileCollection cachedGeneratedFiles;

    public ProcessMarkupTask() {
        getOutputs().upToDateWhen(spec -> getGeneratedFiles().getFiles().stream().allMatch(File::exists));
    }

    @Internal
    public abstract Property<SourceSet> getSourceSet();

    @InputFiles
    @SkipWhenEmpty
    @IgnoreEmptyDirectories
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileTree getSourceFiles() {
        List<File> sourceDirs = new ArrayList<>();
        SourceSet sourceSet = getSourceSet().get();
        File genSrcDir = new PathHelper(getProject()).getGeneratedSourcesDir(sourceSet);

        for (File srcDir : sourceSet.getAllSource().getSrcDirs()) {
            if (!srcDir.getPath().startsWith(genSrcDir.getPath())) {
                sourceDirs.add(srcDir);
            }
        }

        return getProject().files(sourceDirs).getAsFileTree().matching(PATTERN_SET);
    }

    @OutputFiles
    public FileCollection getGeneratedFiles() {
        if (cachedGeneratedFiles != null) {
            return cachedGeneratedFiles;
        }

        Project project = getProject();
        SourceSet sourceSet = getSourceSet().get();
        PathHelper pathHelper = new PathHelper(project);

        try (var compiler = CompilerService.get(project).newCompiler(
                sourceSet, pathHelper.getGeneratedSourcesDir(sourceSet), project.getLogger())) {
            compiler.addFiles(pathHelper.getMarkupFilesPerSourceDirectory(sourceSet));
            return cachedGeneratedFiles = project.files(compiler.getGeneratedJavaFiles());
        } catch (GradleException ex) {
            throw ex;
        } catch (Throwable ex) {
            String message = ex.getMessage();
            throw new GradleException(
                message == null || message.isEmpty() ? "Internal compiler error" : message, ex);
        }
    }

    @TaskAction
    public void process() {
        Project project = getProject();
        SourceSet sourceSet;

        try {
            sourceSet = getSourceSet().get();
        } catch (IllegalStateException ignored) {
            throw new GradleException(String.format(":%s cannot be run in isolation", getName()));
        }

        PathHelper pathHelper = new PathHelper(project);
        File genSrcDir = pathHelper.getGeneratedSourcesDir(sourceSet);
        CompilerService service = CompilerService.get(project);

        ExceptionHelper.run(project, service.newCompiler(sourceSet, genSrcDir, getLogger()), compiler -> {
            // Invoke the addFiles and processFiles stages for the source set.
            // This will generate .java source files that are placed in the generated sources directory.
            compiler.addFiles(pathHelper.getMarkupFilesPerSourceDirectory(sourceSet));
            compiler.processFiles();

            // Delete all .class files that may have been created by a previous compiler run.
            // This is necessary because the FXML compiler needs a 'clean slate' to work with.
            Predicate<Path> fileFilter = path -> path.toString().toLowerCase().endsWith(".java");
            Path classesDir = sourceSet.getJava().getClassesDirectory().get().getAsFile().toPath();

            for (Path file : pathHelper.enumerateFiles(genSrcDir.toPath(), fileFilter)) {
                String fileName = pathHelper.getFileNameWithoutExtension(file.toFile()) + ".class";
                Path relFile = genSrcDir.toPath().relativize(file).getParent().resolve(fileName);
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

        setDidWork(true);
    }

}
