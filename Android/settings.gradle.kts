pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        // Add Android 16 preview repositories
        maven { 
            name = "Google Maven Preview"
            url = uri("https://maven.google.com/")
        }
        maven { 
            name = "AOSP Snapshots"
            url = uri("https://androidx.dev/snapshots/builds/latest/repository")
        }
    }
}

rootProject.name = "Discord"
include(":app")
