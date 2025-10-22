# System Patterns & Architecture

## Layering (from architecture diagram)
- UI: Jetpack Compose screens (Auth, Chat List, Chat, Profile, Group Create).
- State: ViewModels + Kotlin Flows; optimistic local state.
- Services: Auth, Chat, Message, Presence, Image, Notification, Queue.
- Local Storage: Room (messages/chats/queue), file cache (images), DataStore/Keystore for tokens.
- Network: Transport abstraction (Firestore client MVP; prep for Bluetooth/Wi‑Fi Direct). Network monitor feeds queue.
- Modules (pluggable): AI provider, Encryption provider (future upgrades).

## Key Decisions
- DI with Hilt across app and workers; WorkManager configured with HiltWorkerFactory.
- Queue with WorkManager (OneTime for per-message, Periodic for batch sync); exponential backoff.
- Firestore for realtime sync; enable Firestore offline persistence. Optional RTDB for ephemeral presence/typing.
- Media via Firebase Storage; Coil for caching and display.
- Conflict resolution: Last-write-wins by timestamp for MVP.
- Data docs include `metadata: Map<String, Any>` for extensibility.
- Notifications: Prefer in-app Snackbar banner with preview when foreground. Use system notification fallback when app is backgrounded. Store `fcmToken` on user doc at sign-in and on token refresh.

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

