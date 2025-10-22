# Progress

## Status Overview
- Block A: Skeleton (flavors, Hilt, Compose, Room, Auth) — done.
- Block C: Firestore models/paths, LWW utils, mappers, services — done.
- Block D: Paging 3, RemoteMediator, Room indices/remote keys, repository — done.
- Block E: Realtime listeners, delivered/read receipts, RTDB presence/typing, 1:1 chat list + chat UI — largely complete (optimistic send pipeline pending).

## What Works
- Login/register/forgot; logout in top bar.
- Start chat by email/screen name; self-chat supported.
- Real-time chat list sync; open chat; back to list.
- Chat screen with styled bubbles, reverse order, and sending.

## What’s Next
- Block I: Offline support & send queue with retries; Firestore offline persistence; network handling; message state transitions.

## Known Issues / Notes
- Ensure Firestore indexes for timestamp ordering and any future composite queries.

