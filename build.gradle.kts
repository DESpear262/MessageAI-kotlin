//root build file

plugins {
    id("com.android.application") version "8.6.0" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false

    // Required with Kotlin 2.x when compose is enabled
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false

    // Keep these at root so Hilt and KSP share the same classloader
    id("com.google.devtools.ksp") version "2.0.21-1.0.26" apply false
    id("com.google.dagger.hilt.android") version "2.52" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false

    // …your existing root plugins…
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false

}

tasks.register<Delete>("clean") {
    // avoid deprecated buildDir getter warning
    delete(layout.buildDirectory)
}
