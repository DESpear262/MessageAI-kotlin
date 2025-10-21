# Progress

## Status Overview
- Block A: Project skeleton (flavors, Hilt, Compose, Room, Auth) — done.
- Block C: Firestore models/paths, LWW utils, mappers, services — done.
- Block D: Paging 3, RemoteMediator, Room indices/remote keys, repository, schema export — done.

## What Works
- Paging messages (50/pg) with infinite scroll using RemoteMediator.
- Firestore send + list; lastMessage updating on chats.
- Room as source-of-truth; Firestore wiring ready for listeners.

## What’s Next (MVP)
- Block E: Real-time listeners, optimistic send pipeline, message states, presence/typing prep.

## Known Issues / Notes
- Ensure Firestore index for `messages` ordered by `timestamp` exists.
- Presence via RTDB to be implemented in later blocks.

