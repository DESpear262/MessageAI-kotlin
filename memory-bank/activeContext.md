# Active Context

## Current Focus
- Initialize Android Kotlin project skeleton; Firebase setup; DI with Hilt.
- Define data models; Room schema; Firestore wiring.

## Recent Changes
- Memory Bank initialized from PRD, task plan, and architecture diagram.
- .gitignore added for Android/Kotlin and Node/Expo artifacts.

## Next Steps
- Stand up auth flows (register/login/reset) and persistent auth state.
- Implement local DB entities/DAOs and repository layer.
- Wire Firestore listeners and optimistic send pipeline with WorkManager queue.

## Decisions & Considerations
- Use Material3; keep functions <75 lines and files <750 lines as per project rules.
- Timestamp-based LWW for conflict resolution in MVP.

## Risks
- Foreground-only notifications in MVP; background behavior varies by OEM.
- Network variability; ensure robust retry and idempotency in queue.

