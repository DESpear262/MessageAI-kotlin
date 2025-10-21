# Progress

## Status Overview
- Block A completed: Project skeleton, flavors, Hilt, Compose nav, Material3, Room bootstrap, Firebase Auth screen.
- Block C completed: Firestore models (users/chats/groups/messages), paths, time/LWW utilities, mappers, and chat/message services.

## What Works
- Compose shell with Auth→Main and Room database.
- Firestore wiring: models, deterministic chat IDs, LWW timestamp handling, send/list messages, lastMessage update.

## What’s Next (MVP)
- Block B: Auth polish + persistent state and Profile screen (handled by another agent).
- Block D: Repository layer bridging Room/Firestore, pagination (50/pg) and migration stubs.

## Known Issues / Notes
- Place dev/prod `google-services.json` files.
- Presence via RTDB to be implemented in later blocks.

