// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.gradle.compiler;

public final class CompilationUnit extends ReflectiveWrapper {

    private static final MethodRequirement[] REQUIRED_METHODS = {
        new MethodRequirement("descriptor", Object.class),
        new MethodRequirement("generatedSourceText", String.class)
    };

    public CompilationUnit(Object target) {
        super(requireCompatible(target, CompilationUnit.class.getSimpleName(), REQUIRED_METHODS));
    }

    public CompilationUnitDescriptor descriptor() {
        return new CompilationUnitDescriptor(invoke("descriptor"));
    }

    public String generatedSourceText() {
        return (String)invoke("generatedSourceText");
    }
}
