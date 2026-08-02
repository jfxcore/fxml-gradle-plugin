plugins {
    java
    kotlin("jvm") version "2.4.10"
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("com.google.devtools.ksp") version "2.3.10"
    id("org.jfxcore.fxmlplugin")
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

kotlin {
    jvmToolchain(17)
}

javafx {
    modules("javafx.controls")
}

dependencies {
    implementation("org.jfxcore:markup:0.2.0")
}

fxml {
    annotationProcessing = true
}
