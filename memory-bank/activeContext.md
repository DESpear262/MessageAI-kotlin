# Active Context

## Current Focus
- Block D next: Room repositories/migrations and pagination helpers.

## Recent Changes
- Block A completed: Android Kotlin project skeleton created with dev/prod flavors, Hilt, Compose Navigation, Material3 theme, Room bootstrap, and real Firebase Auth screen.
- Block C completed: Firestore data models, deterministic chat IDs, time/LWW helpers, Room mappers, and minimal chat/message services.
- Flavor scaffolding added for `google-services.json` in dev/prod.

## Next Steps
- Implement repository layer bridging Room and Firestore; paging (50/pg) and mappers [Block D].
- Add migration stubs and tests for schema evolution [Block D].

## Decisions & Considerations
- Using Material3. Functions kept under 75 lines; files under 750 lines.
- Flavor-based env selection via `BuildConfig.ENV` (dev/prod). Separate Firebase projects.
- Conflict policy remains timestamp LWW for MVP.
- RTDB will be used for presence in later blocks; Firestore wiring done.

## Risks
- Background notifications behavior varies by OEM; MVP targets foreground.
- Network variability; ensure idempotent queue operations in later blocks.

