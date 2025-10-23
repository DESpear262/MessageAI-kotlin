## MessageAI (Android · Kotlin)

A tactical messaging app focused on reliable, real-time communication with offline-first behavior. MVP delivers 1:1 and group chat, local persistence, media sharing, and foreground notifications. Built natively with Kotlin + Jetpack Compose.

### Key Features (MVP)
- Real-time 1:1 and group chat with optimistic send
- Message states: sending → sent → delivered → read
- Offline-first with Room + WorkManager retry queue
- Image sharing via Firebase Storage
- Foreground push notifications (FCM)

### Tech Stack
- Android: Kotlin, Jetpack Compose, Navigation, Hilt, Room, WorkManager
- Firebase: Auth, Firestore, Realtime Database (presence), Storage, Cloud Messaging (+ Cloud Functions)
- Concurrency: Coroutines + Flow
- Images: Coil

### Repository Layout
- `app/` — Android application (Gradle module)
- `firebase-functions/` — Cloud Functions (notifications, AI gateway)
- `docs/` — Product and architecture docs
- `memory-bank/` — Project intelligence and working context
- Note: An Expo scaffold exists but is legacy; the Android Kotlin app is authoritative.

### Prerequisites
- Android Studio (latest stable; Koala+ recommended)
- JDK 17 (toolchain enforced)
- Gradle 8.11.x (wrapper provided)
- Android Gradle Plugin 8.10.x / Kotlin 2.0.21 (configured)
- Firebase project with Android app `com.messageai.tactical`
- `google-services.json` for each flavor:
  - `app/src/dev/google-services.json`
  - `app/src/prod/google-services.json`

### Setup
1. Clone and open in Android Studio.
2. Ensure `local.properties` has a valid `sdk.dir`.
3. Add Firebase `google-services.json` files to `app/src/dev` and `app/src/prod`.
4. Sync Gradle.

### Run
- Android Studio: select the `devDebug` variant and run on an emulator/device.
- CLI (Windows PowerShell):
  ```powershell
  .\gradlew assembleDevDebug
  .\gradlew installDevDebug
  ```
- CLI (macOS/Linux):
  ```bash
  ./gradlew assembleDevDebug
  ./gradlew installDevDebug
  ```
- Helpers:
  ```bash
  ./gradlew runDev      # assemble, install, and launch Dev Debug on default device
  ./gradlew runDevAll   # deploy Dev Debug to all active devices/emulators
  ```

### Quick Checks
```bash
# Fast compile validation
./gradlew :app:compileDevDebugKotlin

# Lint (dev flavor)
./gradlew :app:lintDevDebug

# Unit / Instrumentation tests (if present)
./gradlew testDevDebugUnitTest
./gradlew connectedDevDebugAndroidTest
```

### Documentation
- Product requirements: `docs/product/messageai-kotlin-prd.md`
- Sprint PRD: `docs/product/messageai-sprint2-prd.md`
- Architecture diagram: `docs/product/messageai-architecture-v2.mermaid`
- Sprint task plan: `docs/product/messageai-sprint2-task-plan.md`

### Troubleshooting
- Keep Compose, Kotlin (2.0.21), and AGP (8.10.x) aligned.
- Ensure `google-services.json` package matches the active flavor applicationId.
- Read the first error first; later errors often cascade.

### License
TBD
