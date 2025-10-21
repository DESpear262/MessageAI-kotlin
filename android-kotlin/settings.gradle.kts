pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.application") version "8.9.0-rc01"
        id("org.jetbrains.kotlin.android") version "1.9.23"
        id("org.jetbrains.kotlin.kapt") version "1.9.23"
        id("org.jetbrains.kotlin.plugin.serialization") version "1.9.23"
        id("com.google.gms.google-services") version "4.4.2"
        id("com.google.dagger.hilt.android") version "2.51.1"
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
