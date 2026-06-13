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

rootProject.name = "popemkt-watch"

// Monorepo shape: every deployable app lives under apps/, shared code under libs/.
include(":apps:watchcal")
// Baseline-profile producer for :apps:watchcal (P0 perf; specs/03-roadmap.md).
include(":apps:watchcal-baselineprofile")
