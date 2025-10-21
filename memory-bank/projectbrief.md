# Project Brief – MessageAI (Kotlin Android)

- Purpose: Tactical messaging with reliable real-time sync, offline-first, and team coordination.
- Primary Platform: Android (Kotlin + Jetpack Compose)
- Delivery: MVP in 24h; full deployment in 7 days.

## MVP Scope (P0)
- Authentication: Email/password, forgot password, persistent session, basic profile.
- One-on-One Chat: Real-time send/receive, optimistic UI, Room persistence, message states, timestamps, presence, typing.
- Group Chat: Create 3+ member groups, name, sender attribution, per-user read receipts, realtime + local history.
- Media (Images): Pick, preview, upload to Firebase Storage, inline display, local caching, progress.
- Push Notifications: Foreground notifications via FCM, deep-link to chat.
- Offline & Sync: View history offline, send queue with WorkManager retry/backoff, graceful reconnect, no loss on crash.

## Success Criteria
- All 8 MVP test scenarios pass.
- 0% message loss in testing; latency targets met (<500ms send; <1s delivery online).
- Stable app (no crashes in normal use); seamless offline→online.

## Key Constraints & Assumptions
- Backend: Firebase (Auth, Firestore, Storage, FCM). Optional RTDB for presence/typing.
- Local: Room + DataStore; WorkManager for queue.
- DI: Hilt; UI: Compose.
- Conflict policy: LWW by timestamp for MVP.

## Non-Goals (Post-MVP)
- E2E encryption (Signal), geolocation/MGRS pins, mesh networking, NATO templates, advanced AI features.
- Background notifications robustness beyond foreground if not required for MVP.

## References
- See `messageai-kotlin-prd.md`, `messageai-mvp-task-plan.md`, and `messageai-architecture.mermaid` at repo root.
