# Progress

## Status Overview
- Block A: Skeleton (flavors, Hilt, Compose, Room, Auth) — done.
- Block C: Firestore models/paths, LWW utils, mappers, services — done.
- Block D: Paging 3, RemoteMediator, Room indices/remote keys, repository — done.
- Block E: Realtime listeners, delivered/read receipts, RTDB presence/typing, 1:1 chat list + chat UI — mostly done.
- Block I (in progress): Offline send queue with WorkManager, Firestore offline persistence.

## What Works
- Background send via SendWorker (constraints + backoff); optimistic local insert.
- Firestore offline persistence enabled.

## What’s Next
- App-start queue re-scan/enqueue; state transitions and lastMessage updates.

## Known Issues / Notes
- Add user-facing error for permanent failures (permission/rules).

