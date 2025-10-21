# Progress

## Status Overview
- Block A: Skeleton (flavors, Hilt, Compose, Room, Auth) — done.
- Block C: Firestore models/paths, LWW utils, mappers, services — done.
- Block D: Paging 3, RemoteMediator, Room indices/remote keys, repository — done.
- Block E (in progress): Per-chat listeners; delivered/read receipt mechanics; RTDB presence/typing — partially complete.
 - Documentation baseline: Headers and KDoc added across Kotlin modules (remote/data, DB, DI, app entry, UI).

## What Works
- Per-chat Firestore listener → Room write-through.
- Delivery acknowledgment (`deliveredBy`) on recipient receive.
- Read receipt updater for fully visible messages (batch `arrayUnion`).
- RTDB presence (`status/{uid}`) and typing (`typing/{chatId}/{uid}`) hooks.

## What’s Next (MVP)
- Optimistic send pipeline + state transitions; lastMessage synchronization.
- Top-of-list new message listener for Paging integration.

## Known Issues / Notes
- Throttle read updates to reduce write volume.
- Confirm security rules for self-only updates on deliveredBy/readBy/typing.

