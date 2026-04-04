## Getting started

##### Using the <a href="https://docs.gradle.org/current/userguide/plugins.html#sec:plugins_block">plugins DSL</a>:

**Groovy**
```groovy
plugins {
    id "org.jfxcore.fxmlplugin" version "0.12.1"
}
```

**Kotlin**
```kotlin
plugins {
    id("org.jfxcore.fxmlplugin") version "0.12.1"
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
    classpath "org.jfxcore:fxml-gradle-plugin:0.12.1"
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
    classpath("org.jfxcore:fxml-gradle-plugin:0.12.1")
  }
}

apply(plugin = "org.jfxcore.fxmlplugin")
```

## Kotlin projects

Kotlin projects must apply the Kotlin Symbol Processing (KSP) plugin explicitly to enable `@Markup` processing:

```kotlin
plugins {
    kotlin("jvm") version "2.3.20"
    id("com.google.devtools.ksp") version "2.3.6"
    id("org.jfxcore.fxmlplugin") version "0.12.1"
}
```
