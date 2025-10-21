# Tech Context

## Stack
- Language/UI: Kotlin + Jetpack Compose; Compose Navigation; Material3.
- DI: Hilt (Dagger).
- Storage: Room + DataStore; Android Keystore for secrets.
- Realtime/Backend: Firebase (Auth, Firestore, Storage, FCM). Optional RTDB for presence.
- Images: Coil.
- Concurrency: Coroutines + Flow.
- Background: WorkManager (+ Hilt integration).

## Build & Deps (from PRD)
- Gradle Kotlin DSL; compose BOM 2023.10.01; Firebase BoM 32.5.0; Room 2.6.0; Hilt 2.48; WorkManager 2.9.0; Coil 2.5.0; DataStore 1.0.0; Coroutines 1.7.3.

## Dev Setup
- Android Studio w/ SDK 34; minSdk 24.
- Firebase project with Auth/Firestore/Storage/FCM; add `google-services.json` to app module (not committed).
- Enable Firestore offline persistence; create notification channels.

## Repo Note
- This repo also includes a React Native/Expo scaffold (TypeScript). Kotlin Android is the target for this implementation; ignore JS artifacts for Android app, but include them in .gitignore.

