# Progress

## Status Overview
- Block A completed: Project skeleton, flavors, Hilt, Compose nav, Material3, Room bootstrap, Firebase Auth screen.
- Block B completed: Login/register (6+ password), display name capture, Firestore user doc creation on register, Forgot Password screen (Firestore existence check + reset email), inline errors, minimal profile display.

## What Works
- Compose shell with Auth→Main and Room database.
- Auth: register, login, logout, persistent session, forgot password flow.
- Firestore users collection documents created on registration.

## What’s Next (MVP)
- Block C: Firestore collections, models, mappers, timestamps, LWW helpers.
- Block D: Repository layer bridging Room/Firestore, pagination (50/pg) and migration stubs.

## Known Issues / Notes
- Place dev/prod `google-services.json` files under `android-kotlin/app/src/dev` and `android-kotlin/app/src/prod`.
- Presence via RTDB to be implemented in later blocks.

