plugins {
    java
    kotlin("jvm") version "2.4.10"
    id("org.jfxcore.fxmlplugin")
}

repositories {
    mavenCentral()
}

fxml {
    annotationProcessing = true
}
