pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Use the Central mirror for the compiler whose download times out on CI's primary route.
        exclusiveContent {
            forRepository {
                maven("https://maven-central.storage-download.googleapis.com/maven2")
            }
            filter {
                includeVersion("org.jetbrains.kotlin", "kotlin-compiler-embeddable", "2.1.0")
            }
        }
        // NewPipeExtractor is published through JitPack
        maven("https://jitpack.io")
    }
}
rootProject.name = "TuneHub"
include(":app")
