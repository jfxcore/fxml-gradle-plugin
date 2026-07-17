## Getting started

##### Using the <a href="https://docs.gradle.org/current/userguide/plugins.html#sec:plugins_block">plugins DSL</a>:

**Groovy**
```groovy
plugins {
    id "org.jfxcore.fxmlplugin" version "0.15.0"
}
```

**Kotlin**
```kotlin
plugins {
    id("org.jfxcore.fxmlplugin") version "0.15.0"
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
    classpath "org.jfxcore:fxml-gradle-plugin:0.15.0"
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
    classpath("org.jfxcore:fxml-gradle-plugin:0.15.0")
  }
}

apply(plugin = "org.jfxcore.fxmlplugin")
```

## Configuration

This plugin registers an extension named `fxml` with the following configuration options:

### `annotationProcessing`

Specifies whether the plugin processes the `@ComponentView` annotation. The default value is `false`.

When this option is enabled, the FXML compiler is added to the annotation processor configuration of each source set.
Kotlin projects must also apply the Kotlin Symbol Processing (KSP) plugin to enable annotation processing.

### `sourceFileExtensions`

Specifies the file extensions used to select FXML source files for compilation. The default value is `fxml`.

Specifying a custom file extension can be used to gradually migrate a project containing legacy FXML files to FXML/2.
For example, the following configuration selects `.fxmlx` files for compilation and leaves `.fxml` files unprocessed
by the FXML compiler:

```kotlin
fxml {
    sourceFileExtensions = listOf("fxmlx")
}
```

It is advisable to use the `fxmlx` extension in migration scenarios, as it is also recognized by the
[FXML/2 IntelliJ IDEA Plugin](https://plugins.jetbrains.com/plugin/32337-fxml-2-for-javafx).
