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

### Local AI (LangChain) + Cloud Functions Emulator

This repo includes a local LangChain-style FastAPI service and a Cloud Functions proxy for secure calls from the Android app.

1) Start LangChain service (FastAPI)
- Terminal 1
  - Windows PowerShell:
    ```powershell
    cd .\langchain-service
    python -m venv .venv
    .\.venv\Scripts\Activate.ps1
    pip install -r requirements.txt
    uvicorn app.main:app --host 127.0.0.1 --port 8000 --reload
    ```
  - macOS/Linux:
    ```bash
    cd langchain-service
    python3 -m venv .venv
    source .venv/bin/activate
    pip install -r requirements.txt
    uvicorn app.main:app --host 127.0.0.1 --port 8000 --reload
    ```

2) Start Firebase Functions emulator
- Terminal 2 (repo root):
  ```bash
  npx --yes firebase-tools@latest emulators:start --only functions
  ```
  Notes:
  - The proxy defaults to `http://127.0.0.1:8000` if `LANGCHAIN_BASE_URL` is not set.
  - `OPENAI_API_KEY` and `LANGCHAIN_SHARED_SECRET` can be configured as Firebase secrets/params for deployed environments; for local dev, defaults are sufficient.

3) Smoke-test the proxy
- Simple router (no auth):
  ```bash
  curl -X POST \
    "http://127.0.0.1:5001/messageai-kotlin/us-central1/aiRouterSimple?path=template/generate" \
    -H "Content-Type: application/json" \
    -d '{"requestId":"test-1","context":{"chatId":"demo"},"payload":{"type":"MEDEVAC","maxMessages":20}}'
  ```
- Secure router (requires Firebase ID token):
  ```bash
  curl -X POST \
    "http://127.0.0.1:5001/messageai-kotlin/us-central1/aiRouter/v1/template/generate" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer <FIREBASE_ID_TOKEN>" \
    -d '{"requestId":"test-2","context":{"chatId":"demo"},"payload":{"type":"MEDEVAC","maxMessages":20}}'
  ```

### Android emulator vs local host

- Android emulator cannot reach `127.0.0.1` on your machine; use `10.0.2.2` instead.
- Example Cloud Functions base URL (emulator):
  - `http://10.0.2.2:5001/messageai-kotlin/us-central1/aiRouter/`
  - `http://10.0.2.2:5001/messageai-kotlin/us-central1/aiRouterSimple?path=...`
- Physical device: use your computer’s LAN IP instead of `10.0.2.2`.

To switch the Android dev build to the emulator host:
- Open `app/build.gradle.kts` and set the dev flavor `CF_BASE_URL` to the emulator URL:
  ```kotlin
  productFlavors {
      create("dev") {
          // ...
          buildConfigField("String", "CF_BASE_URL", "\"http://10.0.2.2:5001/messageai-kotlin/us-central1/\"")
      }
  }
  ```
- Rebuild and run the `devDebug` variant. The app’s Retrofit client will call the emulator.

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
