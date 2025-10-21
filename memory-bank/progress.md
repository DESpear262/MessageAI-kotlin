# Progress

## Status Overview
- Block A completed: Project skeleton, flavors, Hilt, Compose nav, Material3, Room bootstrap, Firebase Auth screen.

## What Works
- App builds (expected) with Compose shell and real email/password auth flow.
- Flavor selection (dev/prod) and ENV flag wiring.
- Room database with entities/DAOs is in place.

## What’s Next (MVP)
- Block B: Auth flows polish + persistent state and Profile screen.
- Block C: Firestore collections, models, mappers, timestamps, LWW helpers.
- Block D: Repo layer, pagination, migration stubs and tests.

## Known Issues / Notes
- Requires placing dev/prod `google-services.json` files to run against Firebase.
- Background notifications not targeted in MVP Block A.

