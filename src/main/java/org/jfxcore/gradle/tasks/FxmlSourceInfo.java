// Copyright (c) 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.gradle.tasks;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.SkipWhenEmpty;

public abstract class FxmlSourceInfo {

    @Internal
    public abstract DirectoryProperty getSourceDir();

    @InputFiles
    @SkipWhenEmpty
    public abstract Property<FileCollection> getFxmlFiles();
}
