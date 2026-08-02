rootProject.name = "multi-project-functional-test"
include("app", "model")

buildCache {
    local {
        directory = file(".gradle/build-cache")
    }
}
