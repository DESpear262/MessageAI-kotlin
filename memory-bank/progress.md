# Progress

## Status Overview
- Block A: Skeleton (flavors, Hilt, Compose, Room, Auth) — done.
- Block C: Firestore models/paths, LWW utils, mappers, services — done.
- Block D: Paging 3, RemoteMediator, Room indices/remote keys, repository — done.
- Block E: Realtime listeners, delivered/read receipts, RTDB presence/typing, 1:1 chat list + chat UI — mostly done.
- Block G: Media (images) — done (gallery + camera, upload, inline render, prefetch).
- Block I: Offline support & send queue — done.
- Block J: Presence indicator dots in list/header — done.

## What Works
- Text send pipeline: optimistic local insert, background send via SendWorker (constraints + backoff), idempotent Firestore writes, lastMessage updates.
- Image pipeline: pick/capture → cache copy → resize/compress (LWW timestamps honored) → Storage upload → patch message with `imageUrl` and `status=SENT`.
- UI: Inline images via Coil with prefetch; simple pending preview and send; chat bubbles adapt to image or text.
- WorkManager + Hilt WorkerFactory configured; workers resilient to restarts.
- Presence dots (green online, gray offline) using RTDB `status/{uid}`.

## What’s Next
- Finalize Storage/Firestore rules for media once text policy is ready; integrate shared policy.
- Optional: add progress % and oversize warning UX, and a full-screen preview after MVP.
- Choose next: F (Groups), G (Media), or H (Notifications) refinements if any remain.

## Known Issues / Notes
- Indeterminate progress for uploads (acceptable for MVP).
- Security rules pending coordination; test media strictly in dev until policies land.

