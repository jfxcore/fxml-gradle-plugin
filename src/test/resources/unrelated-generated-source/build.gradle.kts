plugins {
    java
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("org.jfxcore.fxmlplugin")
}

repositories {
    mavenCentral()
}

javafx {
    modules("javafx.controls")
}

fxml {
    annotationProcessing = true
}

abstract class GenerateSources : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        outputDirectory.file("Generated.source").get().asFile.apply {
            parentFile.mkdirs()
            writeText("generated")
        }
    }
}

val generatedSourcesDir = layout.buildDirectory.dir("generated/sources/external/main")
val generateSources = tasks.register<GenerateSources>("generateSources") {
    outputDirectory.set(generatedSourcesDir)
}

sourceSets.named("main") {
    // This is deliberately a raw path rather than generateSources.flatMap(...).
    allSource.srcDir(generatedSourcesDir)
}

tasks.named("classes") {
    dependsOn(generateSources)
}
