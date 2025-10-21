# Active Context

## Current Focus
- Block E next: Real-time 1:1 chat wiring (listeners, optimistic send pipeline, states).

## Recent Changes
- Block A completed: Android Kotlin project skeleton with flavors, Hilt, Compose Navigation, Material3 theme, Room bootstrap, and Firebase Auth screen.
- Block C completed: Firestore models, deterministic chat IDs, time/LWW helpers, Room mappers, minimal chat/message services.
- Block D completed: Paging 3 integration with RemoteMediator, Room indices and remote keys, repository API, schema export config.

## Next Steps
- Implement realtime listeners (Firestore) → Room write-through; optimistic send with queue; message state transitions [Block E].
- Presence/typing to follow later (RTDB).

## Decisions & Considerations
- Material3; function/file size constraints maintained.
- `BuildConfig.ENV` for dev/prod; separate Firebase projects.
- LWW policy: server timestamp preferred; clientTimestamp fallback.

## Risks
- Pagination relies on `timestamp` ordering; ensure Firestore indexes exist for combined queries when added.
- Network variability; ensure idempotency in send pipeline next.

