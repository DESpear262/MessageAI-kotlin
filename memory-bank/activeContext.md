# Active Context

## Current Focus
- Block D next: Room repositories/migrations and pagination helpers.

## Recent Changes
- Block A completed: Android Kotlin project skeleton created with dev/prod flavors, Hilt, Compose Navigation, Material3 theme, Room bootstrap, and real Firebase Auth screen.
- Block B completed: Auth validation (6+ chars), register with display name, Firestore user doc on register, Forgot Password screen with Firestore existence check, inline error UX, minimal profile stub in main. Session persistence via FirebaseAuth defaults.
- Flavor scaffolding added for `google-services.json` in dev/prod.

## Next Steps
- Implement repository layer bridging Room and Firestore; paging (50/pg) and mappers [Block D].
- Add migration stubs and tests for schema evolution [Block D].

## Decisions & Considerations
- Using Material3. Functions kept under 75 lines; files under 750 lines.
- Flavor-based env selection via `BuildConfig.ENV` (dev/prod). Separate Firebase projects.
- Conflict policy remains timestamp LWW for MVP.
- Auth: no email verification for MVP; no Keystore token storage. Forgot password uses Firestore existence check then Firebase email send.

## Risks
- Background notifications behavior varies by OEM; MVP targets foreground.
- Network variability; ensure idempotent queue operations in later blocks.

