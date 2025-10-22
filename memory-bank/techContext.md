# Tech Context

## Stack
- Language/UI: Kotlin + Jetpack Compose; Compose Navigation; Material3.
- DI: Hilt (Dagger) with Hilt WorkerFactory for WorkManager.
- Storage: Room + DataStore; Android Keystore for secrets.
- Realtime/Backend: Firebase (Auth, Firestore, Storage, FCM). Optional RTDB for presence.
- Images: Coil (compose) + ExifInterface (for metadata handling/rotation if needed).
- Concurrency: Coroutines + Flow.
- Background: WorkManager (+ Hilt integration).

## Build & Deps (effective)
- Gradle Kotlin DSL; compose BOM 2024.09.02; Firebase BoM 33.4.0; Room 2.6.1; Hilt 2.51.1; WorkManager 2.9.0; Coil 2.5.0; DataStore 1.0.0; Coroutines 1.9.0; Paging 3.3.2; exifinterface 1.3.7.

## Dev Setup
- Android Studio; compileSdk 35; minSdk 24.
- Firebase project with Auth/Firestore/Storage/FCM; add `google-services.json` to app module per flavor.
- Firestore offline persistence enabled; notification channel created at app start.

## Repo Note
- This repo also includes a React Native/Expo scaffold (TypeScript). Kotlin Android is the target for this implementation; ignore JS artifacts for Android app, but include them in .gitignore.

