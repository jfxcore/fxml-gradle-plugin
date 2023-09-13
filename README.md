## Getting started

To use the plugin, apply the following two steps:

##### Using the <a href="https://docs.gradle.org/current/userguide/plugins.html#sec:plugins_block">plugins DSL</a>:

**Groovy**
```groovy
plugins {
    id "org.jfxcore.fxmlplugin" version "0.8.1"
}
```

**Kotlin**
```kotlin
plugins {
    id("org.jfxcore.fxmlplugin") version "0.8.1"
}
```

##### Using <a href="https://docs.gradle.org/current/userguide/plugins.html#sec:old_plugin_application">legacy plugin application</a>:

**Groovy**
```groovy
buildscript {
  repositories {
    maven {
      url "https://plugins.gradle.org/m2/"
    }
  }
  dependencies {
    classpath "org.jfxcore:fxml-gradle-plugin:0.8.1"
  }
}

apply plugin: "org.jfxcore.fxmlplugin"
```

**Kotlin**
```kotlin
buildscript {
  repositories {
    maven {
      url = uri("https://plugins.gradle.org/m2/")
    }
  }
  dependencies {
    classpath("org.jfxcore:fxml-gradle-plugin:0.8.1")
  }
}

apply(plugin = "org.jfxcore.fxmlplugin")
```