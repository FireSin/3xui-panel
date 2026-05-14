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

rootProject.name = "3xui-panel"

include(":app")

// core modules
include(":core:common")
include(":core:designsystem")
include(":core:data")
include(":core:network")
include(":core:crypto")
include(":core:xui")
include(":core:sampler")

// feature modules
include(":feature:panels")
include(":feature:dashboard")
include(":feature:inbounds")
include(":feature:clients")
include(":feature:share")
include(":feature:stats")
include(":feature:lock")
include(":feature:settings")
include(":feature:nodes")
