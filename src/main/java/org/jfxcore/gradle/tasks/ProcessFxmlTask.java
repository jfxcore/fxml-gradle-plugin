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
import org.jfxcore.compiler.runner.ClassGeneratorRunner;
import org.jfxcore.compiler.runner.CompilationUnitDescriptorWrapper;
import org.jfxcore.compiler.runner.CompilationUnitWrapper;
import org.jfxcore.compiler.runner.RunnerException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        Set<Path> searchPath = getSearchPath().get().getFiles().stream().map(File::toPath).collect(Collectors.toSet());
        File intermediateBuildDir = getIntermediateBuildDir().get().getAsFile();
        File genSrcDir = getGeneratedSourcesDir().get().getAsFile();
        File classesDir = getClassesDir().get().getAsFile();

        try (var generator = new ClassGeneratorRunner(searchPath, new GradleLoggerAdapter(getLogger()))) {
            Map<Path, List<Path>> files = getFxmlSourceInfo().get().stream()
                .collect(Collectors.toUnmodifiableMap(
                    x -> x.getSourceDir().get().getAsFile().toPath(),
                    x -> x.getFxmlFiles().get().getFiles().stream().map(File::toPath).toList()));

            generator.addFileSources(files);

            for (CompilationUnitWrapper compilationUnit : generator.process()) {
                CompilationUnitDescriptorWrapper descriptor = compilationUnit.descriptor();
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
        } catch (RunnerException ex) {
            throw new GradleException(ex.getMessage());
        } catch (Throwable ex) {
            throw new GradleException("Internal compiler error", ex);
        }

        setDidWork(true);
    }
}
