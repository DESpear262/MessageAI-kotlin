# Progress

## Status Overview
- Block A: Skeleton (flavors, Hilt, Compose, Room, Auth) — done.
- Block C: Firestore models/paths, LWW utils, mappers, services — done.
- Block D: Paging 3, RemoteMediator, Room indices/remote keys, repository — done.
- Block E (in progress): Listeners, delivery/read receipts, RTDB presence/typing; 1:1 chat list + start chat — partially complete.

## What Works
- Login/register/forgot flows; post-auth main shows chat list.
- Start chat by email or screen name; creates/ensures direct chat with details.
- Self-chat supported (chat labeled "Note to self").
- Chat screen with newest-at-top and sending to Firestore; paging for history.

## What’s Next (MVP)
- Optimistic send state transitions; lastMessage updates on send and previews on list.
- Top-of-list new message listener integration.

## Known Issues / Notes
- Display name uniqueness not enforced; using `displayNameLower` lookup.

