buildscript {
    dependencies {
        "classpath"(files("../../build/libs/fxml-gradle-plugin-1.0-SNAPSHOT.jar"))
        "classpath"(group = "org.jfxcore", name = "fxml-compiler", version = "0.8.7")
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
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

application {
    mainClass.set("org.example.App")
}

javafx {
    modules("javafx.controls")
}
