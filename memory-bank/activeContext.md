# Active Context

## Current Focus
- Block B–D next: Authentication flows, Firestore wiring/models, Room repositories/migrations.

## Recent Changes
- Block A completed: Android Kotlin project skeleton created with dev/prod flavors, Hilt, Compose Navigation, Material3 theme, Room bootstrap, and real Firebase Auth screen.
- Flavor scaffolding added for `google-services.json` in dev/prod.

## Next Steps
- Implement full Authentication (register/login/logout/reset + persistent auth) [Block B].
- Define Firestore collections/models/mappers and time helpers; LWW policy [Block C].
- Flesh out Room repositories and migration stubs; pagination helpers [Block D].

## Decisions & Considerations
- Using Material3. Functions kept under 75 lines; files under 750 lines.
- Flavor-based env selection via `BuildConfig.ENV` (dev/prod). Separate Firebase projects.
- Conflict policy remains timestamp LWW for MVP.

## Risks
- Background notifications behavior varies by OEM; MVP targets foreground.
- Network variability; ensure idempotent queue operations in later blocks.

