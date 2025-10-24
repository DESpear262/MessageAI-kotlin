# System Patterns & Architecture

## Layering (from architecture diagram)
- UI: Jetpack Compose screens (Auth, Chat List, Chat, Profile, Group Create).
- State: ViewModels + Kotlin Flows; optimistic local state.
- Services: Auth, Chat, Message, Presence, Image, Notification, Queue.
  - **AI (Sprint 2):** Provider interface + Retrofit adapter; RAG context builder with configurable window; LangChain integration; WorkManager offline queue.
- Local Storage: Room (messages/chats/queue), file cache (images), DataStore/Keystore for tokens.
- Network: Transport abstraction (Firestore client MVP; prep for Bluetooth/Wi‑Fi Direct). Network monitor feeds queue.
- Modules (pluggable): AI provider, Encryption provider (future upgrades).
  - AI defaults: Retrofit/OkHttp/Moshi; Firebase ID token bearer; feature-flag via BuildConfig; LocalProvider for offline testing.

## Key Decisions
- Account isolation on device:
  - All Firestore real-time listeners (chats/messages) are stopped on logout and on chat-list dispose.
  - Local persistence (Room) is cleared on logout from a background dispatcher to avoid main-thread violations.
  - UI chat list Flow is additionally guarded by current Firebase UID to avoid rendering entries not containing the signed-in user.
- DI with Hilt across app and workers; WorkManager configured with HiltWorkerFactory.
- Queue with WorkManager (OneTime for per-message, Periodic for batch sync); exponential backoff.
- Firestore for realtime sync; enable Firestore offline persistence. Optional RTDB for ephemeral presence/typing.
- Media via Firebase Storage; Coil for caching and display.
- AI: Separate REST paths behind Cloud Function proxy; request envelope `{requestId, context, payload}` and matching response envelope; requestId logged client-side.
- CF AI Gateway:
  - `aiRouter` (/v1/*) with Firebase ID token verification, HMAC signing headers (`x-sig`, `x-sig-ts`), per‑UID token-bucket rate limiting, 64KB payload cap, per-endpoint timeouts, structured logs.
  - `aiRouterSimple?path=...` for local testing.
  - Defaults to local `http://127.0.0.1:8000` when `LANGCHAIN_BASE_URL` is not set.

## Media Pipeline Patterns
- Deterministic storage paths: `chat-media/{chatId}/{messageId}.jpg` using deterministic chat IDs and UUID message IDs.
- Client processing: copy to app cache → decode (HEIC via ImageDecoder on API 28+) → resize to max edge 2048 → JPEG compress quality ~85 → EXIF stripped by re-encode.
- Two-step message lifecycle: create/merge message as `SENDING` (no URL) → upload image → patch `imageUrl` and set `status=SENT`.
- Metadata-based policy alignment: include `chatId`, `messageId`, `senderId` as Storage custom metadata for rule enforcement alongside path validation.
- Prefetch strategy: prefetch image URLs for visible + next ~6 messages to smooth scroll.

## Data Model (essentials)
- users, chats (participants + details, lastMessage), messages (status, readBy, imageUrl), groups (members, memberDetails), send_queue (local).

## Testing Patterns
- Dual device/emulator tests for realtime; offline/online transitions; crash/force-quit resilience; burst traffic; group receipts; image pipeline; persistence.
- Notifications: Verify foreground banner shows and navigates; background notification taps open the correct chat.

## Post-MVP Hooks
- Transport interface for mesh; Encryption provider for Signal; AI module; tactical features (geo, self-destruct, NATO templates).

