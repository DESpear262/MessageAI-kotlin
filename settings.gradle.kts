pluginManagement {
    plugins {
        // Android Gradle Plugin (stable)
        id("com.android.application") version "8.10.0"
        id("com.android.library")     version "8.10.0"

        // Kotlin (classic plugins)
        id("org.jetbrains.kotlin.android") version "2.0.21"
        id("org.jetbrains.kotlin.kapt")    version "2.0.21"
        id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"

        // Google / Hilt plugins (optional: only if you actually use them in :app)
        id("com.google.dagger.hilt.android") version "2.52"          // match your Hilt dep
        id("com.google.gms.google-services") version "4.4.2"         // Google Services plugin
    }
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MessageAI-Kotlin"
include(":app")
