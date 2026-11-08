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
        exclusiveContent {
            forRepository {
                maven("https://maven-central.storage-download.googleapis.com/maven2")
            }
            filter {
                includeVersion("org.jetbrains.kotlin", "kotlin-compiler-embeddable", "2.1.0")
            }
        }
        maven("https://jitpack.io")
    }
}
rootProject.name = "TuneHub"
include(":app")
