plugins {
    java
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("org.jfxcore.fxmlplugin")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

javafx {
    modules("javafx.controls")
}

dependencies {
    implementation(project(":model"))
}
