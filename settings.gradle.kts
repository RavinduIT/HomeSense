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
    }
}

rootProject.name = "HomeSense"
include(":app")

// /worker (Node + TypeScript) and /simulator (static HTML) are deliberately NOT
// Gradle modules. They are separate runtimes with their own toolchains; see
// docs/SCHEMA.md for the contract that binds all three together.
