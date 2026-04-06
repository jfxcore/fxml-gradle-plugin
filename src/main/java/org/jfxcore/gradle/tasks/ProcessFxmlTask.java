// Copyright (c) 2023, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.gradle.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.jfxcore.gradle.compiler.CompilationUnit;
import org.jfxcore.gradle.compiler.CompilationUnitDescriptor;
import org.jfxcore.gradle.compiler.ExceptionHelper;
import org.jfxcore.gradle.compiler.ClassGenerator;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class ProcessFxmlTask extends DefaultTask {

    public static final String VERB = "process";
    public static final String TARGET = "fxml";

    @Internal
    public abstract Property<FileCollection> getSearchPath();

    @InputFiles
    public abstract Property<FileCollection> getCompileClasspath();

    @Nested
    public abstract ListProperty<FxmlSourceInfo> getFxmlSourceInfo();

    @OutputDirectory
    public abstract DirectoryProperty getClassesDir();

    @OutputDirectory
    public abstract DirectoryProperty getGeneratedSourcesDir();

    @OutputDirectory
    public abstract DirectoryProperty getIntermediateBuildDir();

    @TaskAction
    public void process() {
        FileCollection searchPath = getSearchPath().get();
        File intermediateBuildDir = getIntermediateBuildDir().get().getAsFile();
        File genSrcDir = getGeneratedSourcesDir().get().getAsFile();
        File classesDir = getClassesDir().get().getAsFile();
        ExceptionHelper exceptionHelper = null;

        try (var generator = new ClassGenerator(searchPath.getFiles(), getLogger())) {
            exceptionHelper = generator.getExceptionHelper();

            Map<File, List<File>> files = getFxmlSourceInfo().get().stream()
                .collect(Collectors.toUnmodifiableMap(
                    x -> x.getSourceDir().get().getAsFile(),
                    x -> x.getFxmlFiles().get().getFiles().stream().toList()));

            generator.addFileSources(files);

            for (CompilationUnit compilationUnit : generator.process()) {
                CompilationUnitDescriptor descriptor = compilationUnit.descriptor();
                File classFile = descriptor.resolveMarkupFile(classesDir, ".class");
                Path sourceFile = descriptor.resolveMarkupFile(genSrcDir, ".java").toPath();

                // Delete all .class files that may have been created by a previous compiler run.
                // This is necessary because the FXML compiler needs a 'clean slate' to work with.
                if (classFile.exists()) {
                    classFile.delete();
                }

                // Generate the .java stub classes in the generated sources directory.
                // These files will be compiled by the Java compiler before the FXML compiler runs.
                Files.createDirectories(sourceFile.getParent());
                Files.writeString(
                    sourceFile,
                    compilationUnit.generatedSourceText(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);

                // Generate the .fxmd files that are placed in the intermediate build directory.
                // These files will be picked up by the FXML compiler after the Java compiler has finished, and
                // contain information that the FXML compiler needs to rewrite the bytecode of the stub classes.
                compilationUnit.descriptor().writeTo(intermediateBuildDir);
            }
        } catch (Throwable ex) {
            ExceptionHelper.handleException(exceptionHelper, ex, getLogger());
            throw new GradleException("Internal compiler error", ex);
        }

        setDidWork(true);
    }
}
