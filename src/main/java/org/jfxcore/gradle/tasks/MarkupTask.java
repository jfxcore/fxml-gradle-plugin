// Copyright (c) 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.gradle.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.SourceSet;

public abstract class MarkupTask extends DefaultTask {

    public static final String TARGET = "fxml";

    @Internal
    public abstract Property<SourceSet> getSourceSet();

}
