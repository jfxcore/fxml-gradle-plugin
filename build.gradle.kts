plugins {
    id("signing")
    id("com.gradle.plugin-publish") version("2.0.0")
}

group = "org.jfxcore"
version = findProperty("TAG_VERSION") ?: "1.0-SNAPSHOT"

val compilerVersion = version

java {
    withSourcesJar()
    withJavadocJar()
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation(gradleApi())
    implementation("org.jfxcore:fxml-compiler:$compilerVersion")

    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val writePluginMetadata by tasks.registering(WriteProperties::class) {
    destinationFile.set(layout.buildDirectory.file("generated/plugin-metadata/plugin.properties"))
    property("compiler-version", compilerVersion)
}

sourceSets.main {
    output.dir(writePluginMetadata)
}

tasks.test {
    useJUnitPlatform()
    dependsOn(tasks.jar)
}

tasks.processResources {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

tasks.withType(GenerateModuleMetadata::class) {
    enabled = false
}

gradlePlugin {
    website.set("https://github.com/jfxcore/fxml-gradle-plugin")
    vcsUrl.set("https://github.com/jfxcore/fxml-gradle-plugin")

    plugins {
        create("fxmlPlugin") {
            id = "org.jfxcore.fxmlplugin"
            displayName = "FXML Gradle Plugin"
            description = "Supports creating FXML-based user interfaces with JavaFX"
            implementationClass = "org.jfxcore.gradle.CompilerPlugin"
            tags.set(listOf("javafx", "jfxcore", "fxml"))
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("pluginMaven") {
            pom {
                url.set("https://github.com/jfxcore/fxml-gradle-plugin")
                name.set("fxml-gradle-plugin")
                description.set("Supports creating FXML-based user interfaces with JavaFX")

                licenses {
                    license {
                        name.set("BSD-3-Clause")
                        url.set("https://opensource.org/licenses/BSD-3-Clause")
                    }
                }

                developers {
                    developer {
                        id .set("jfxcore")
                        name.set("JFXcore")
                        organization.set("JFXcore")
                        organizationUrl.set("https://github.com/jfxcore")
                    }
                }

                scm {
                    connection.set("scm:git:https://github.com/jfxcore/fxml-gradle-plugin.git")
                    developerConnection.set("scm:git:https://github.com/jfxcore/fxml-gradle-plugin.git")
                    url.set("https://github.com/jfxcore/fxml-gradle-plugin")
                }
            }
        }
    }

    repositories {
        maven {
            if (project.hasProperty("REPOSITORY_USERNAME")
                    && project.hasProperty("REPOSITORY_PASSWORD")
                    && project.hasProperty("REPOSITORY_URL")) {
                credentials {
                    username = project.property("REPOSITORY_USERNAME") as String
                    password = project.property("REPOSITORY_PASSWORD") as String
                }

                url = uri(project.property("REPOSITORY_URL") as String)
            }
        }
    }
}

signing {
    sign(publishing.publications["pluginMaven"])
}

tasks.withType<Sign>().configureEach {
    val taskNames = gradle.startParameter.taskNames.map { it.substringAfterLast(':') }
    val publishToMavenLocal = taskNames.isNotEmpty() && taskNames.all { name -> name == "publishToMavenLocal" }
    onlyIf {
        !publishToMavenLocal
    }
}
