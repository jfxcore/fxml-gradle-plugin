buildscript {
    dependencies {
        "classpath"(files("../../build/libs/fxml-gradle-plugin-1.0-SNAPSHOT.jar"))
        "classpath"(group = "org.jfxcore", name = "fxml-compiler", version = "0.12.1")
    }
}

plugins {
    id("java")
    id("application")
    id("org.openjfx.javafxplugin") version "0.1.0"
}

apply<org.jfxcore.gradle.CompilerPlugin>()

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

application {
    mainClass.set("org.example.App")
}

javafx {
    modules("javafx.controls")
}
