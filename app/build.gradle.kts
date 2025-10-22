//App build file

plugins {
    id("com.android.application")
    kotlin("android")
    // Compose compiler plugin is required on Kotlin 2.x
    id("org.jetbrains.kotlin.plugin.compose")
    // Hilt + KSP (do NOT also apply kapt)
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    // Firebase google-services
    id("com.google.gms.google-services")
    id("org.jetbrains.kotlin.plugin.serialization")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    jvmToolchain(17)
}

android {
    namespace = "com.messageai.tactical" // base; flavors override below when needed
    compileSdk = 35

    defaultConfig {
        applicationId = "com.messageai.tactical"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        vectorDrawables { useSupportLibrary = true }
    }

    // Do NOT add a debug applicationIdSuffix; it causes google-services.json
    // to require a com.messageai.tactical.dev.debug client.
    buildTypes {
        debug {
            // leave applicationIdSuffix empty to keep package = com.messageai.tactical.dev
            // applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // dev is default flavor
    flavorDimensions += "env"
    productFlavors {
        create("dev") {
            dimension = "env"
            applicationIdSuffix = ".dev"
            // Namespace per-flavor to avoid manifest pkg usage
            @Suppress("UnstableApiUsage")
            namespace = "com.messageai.tactical.dev"
            // This is where your dev google-services.json lives
            // app/src/dev/google-services.json
        }
        create("prod") {
            dimension = "env"
            // No suffix — matches prod google-services.json client
            @Suppress("UnstableApiUsage")
            namespace = "com.messageai.tactical"
            // app/src/prod/google-services.json
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        // Use the Kotlin Compose Compiler plugin (no explicit version string here)
        // The plugin version comes from the root libs or your plugin portal.
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
        // for Hilt/Room incremental KSP, generally nothing else needed
    }
    
    sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")

    // If you have source sets already, you can keep them; the defaults work for most projects:
    // src/dev/..., src/prod/..., src/main/...
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.generateKotlin", "true")
}

dependencies {
    // Room core (you likely already have these)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // ✅ Needed for PagingSource in @Dao
    implementation("androidx.room:room-paging:2.6.1")

    // ----- AndroidX core/UI -----
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    // Compose BOM + UI
    implementation(platform("androidx.compose:compose-bom:2024.09.02"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.1")

    // Desugaring (if you use java.time, etc.)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.2")

    // ----- Hilt (KSP) -----
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-android-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // ----- Firebase (via BoM) -----
    implementation(platform("com.google.firebase:firebase-bom:33.4.0"))
    // Add only what your DI provides/uses:
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-database-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    // If you sign in with Google:
    // implementation("com.google.android.gms:play-services-auth:21.2.0")

    // ----- Room (for AppDatabase) -----
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // ----- Paging (for RemoteMediator superclass) -----
    implementation("androidx.paging:paging-runtime-ktx:3.3.2")

    // ----- Retrofit/OkHttp (for *Service classes if you use them) -----
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // ----- Coroutines -----
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // Kotlinx Serialization (JSON)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")


    // ----- Testing (optional placeholders) -----
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.02"))
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    implementation("androidx.compose.foundation:foundation")
}

// Keep Kotlin at 17 for all Kotlin compile tasks (covers KSP too)
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "17"
}
