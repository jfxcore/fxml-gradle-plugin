plugins {
    java
    id("org.openjfx.javafxplugin") version "0.1.0"
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

javafx {
    modules("javafx.controls")
}

dependencies {
    implementation("org.jfxcore:markup:0.2.0")
}

fxml {
    annotationProcessing = false
}

val integrationTest by sourceSets.creating

configurations[integrationTest.implementationConfigurationName]
    .extendsFrom(configurations.implementation.get())
