// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.gradle;

import org.gradle.api.provider.Property;

public abstract class CompilerPluginExtension {

    static final String NAME = "fxml";

    /**
     * Controls whether the plugin processes the {@code ComponentView} annotation.
     */
    public abstract Property<Boolean> getAnnotationProcessing();
}
