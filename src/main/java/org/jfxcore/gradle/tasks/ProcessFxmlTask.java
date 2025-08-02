// Copyright (c) 2023, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.gradle.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.services.ServiceReference;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.jfxcore.gradle.PathHelper;
import org.jfxcore.gradle.compiler.Compiler;
import org.jfxcore.gradle.compiler.CompilerService;
import java.io.File;
import java.util.UUID;

public abstract class ProcessFxmlTask extends DefaultTask {

    public static final String VERB = "process";
    public static final String TARGET = "fxml";

    @ServiceReference(CompilerService.NAME)
    protected abstract Property<CompilerService> getCompilerService();

    @Internal
    public abstract Property<UUID> getCompilationId();

    @Internal
    public abstract Property<FileCollection> getSearchPath();

    @InputFiles
    @SkipWhenEmpty
    @IgnoreEmptyDirectories
    public abstract Property<FileCollection> getSourceDirs();

    @OutputDirectory
    public abstract DirectoryProperty getClassesDir();

    @OutputDirectory
    public abstract DirectoryProperty getGeneratedSourcesDir();

    @TaskAction
    public void process() {
        UUID compilationId = getCompilationId().get();
        FileCollection searchPath = getSearchPath().get();
        File classesDir = getClassesDir().get().getAsFile();
        File genSrcDir = getGeneratedSourcesDir().get().getAsFile();
        CompilerService service = getCompilerService().get();
        Compiler compiler = service.newCompiler(compilationId, searchPath, classesDir, genSrcDir, getLogger());

        try {
            // Invoke the addFiles and processFiles stages for the source set.
            // This will generate .java source files that are placed in the generated sources directory.
            compiler.addFiles(PathHelper.getFxmlFilesPerSourceDirectory(getSourceDirs().get().getFiles(), genSrcDir));
            compiler.processFiles();

            // Delete all .class files that may have been created by a previous compiler run.
            // This is necessary because the FXML compiler needs a 'clean slate' to work with.
            compiler.getCompilationUnits().getMarkupClassFiles().forEach(File::delete);
        } catch (Throwable ex) {
            compiler.getExceptionHelper().handleException(ex, getLogger());
            compiler.close();
            throw new GradleException("Internal compiler error", ex);
        }

        setDidWork(true);
    }
}
