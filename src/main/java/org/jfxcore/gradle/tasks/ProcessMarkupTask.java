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
import java.io.File;
import java.util.ArrayList;
import java.util.List;

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
                project, sourceSet, pathHelper.getGeneratedSourcesDir(sourceSet), project.getLogger())) {
            compiler.addFiles(pathHelper.getMarkupFilesPerSourceDirectory(sourceSet));
            return cachedGeneratedFiles = project.files(compiler.getCompilationUnits().getJavaFiles());
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
        SourceSet sourceSet = getSourceSet().get();
        PathHelper pathHelper = new PathHelper(project);
        File genSrcDir = pathHelper.getGeneratedSourcesDir(sourceSet);
        CompilerService service = CompilerService.get(project);
        var compiler = service.newCompiler(project, sourceSet, genSrcDir, getLogger());

        try {
            // Invoke the addFiles and processFiles stages for the source set.
            // This will generate .java source files that are placed in the generated sources directory.
            compiler.addFiles(pathHelper.getMarkupFilesPerSourceDirectory(sourceSet));
            compiler.processFiles();

            // Delete all .class files that may have been created by a previous compiler run.
            // This is necessary because the FXML compiler needs a 'clean slate' to work with.
            compiler.getCompilationUnits().getClassFiles().forEach(File::delete);
        } catch (Throwable ex) {
            compiler.getExceptionHelper().handleException(ex, getLogger());
            throw new GradleException("Internal compiler error", ex);
        } finally {
            compiler.close();
        }

        setDidWork(true);
    }

}
