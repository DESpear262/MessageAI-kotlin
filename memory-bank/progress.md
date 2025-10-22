# Progress

## Status Overview
- Block A: Skeleton (flavors, Hilt, Compose, Room, Auth) — done.
- Block C: Firestore models/paths, LWW utils, mappers, services — done.
- Block D: Paging 3, RemoteMediator, Room indices/remote keys, repository — done.
- Block E: Realtime listeners, delivered/read receipts, RTDB presence/typing, 1:1 chat list + chat UI — mostly done.
- Block I: Offline support & send queue — done.

## What Works
- Background send via SendWorker (constraints + backoff); optimistic local insert; idempotent Firestore writes.
- Firestore offline persistence; queue re-scan on app start; SENDING→SENT state updates; lastMessage sync.

## What’s Next
- Choose next: F (Groups), G (Media), or H (Notifications).

## Known Issues / Notes
- Add user-facing FAILED state and manual retry in a later pass.

