# Progress

## Status Overview
### Sprint 2 - AI Integration
- **Block A: AI Core Module** — ✅ COMPLETE & QC APPROVED
  - Provider interface & facade pattern
  - RAG context builder with configurable windows
  - LangChain HTTP adapter (5 endpoints)
  - Hilt DI with Firebase Auth integration
  - WorkManager offline queue
  - 32 unit tests (100% pass)
  - Developer README + QC report

### MVP Sprint (Complete)
- Block C: Firestore models/paths, LWW utils, mappers, services — done.
- Block D: Paging 3, RemoteMediator, Room indices/remote keys, repository — done.
- Block E: Realtime listeners, delivered/read receipts, RTDB presence/typing, 1:1 chat list + chat UI — done.
- Block F: Group chat — done (unified list, group create/rename, sender attribution per bubble, presence header).
- Block G: Media (images) — done (gallery + camera, upload, inline render, prefetch).
- Block I: Offline support & send queue — done.
- Block J: Presence indicator dots — done.

## What Works
- **AI Module (NEW):** Provider-swappable architecture; RAG context from Room; LangChain adapter; secure token handling; offline queue; LocalProvider for testing.
- **Core Chat:** Text send pipeline; image pipeline; UI image rendering; WorkManager + Hilt integration; presence dots via RTDB.
- **Test Suite:** 71/72 tests passing (98% success rate); Block A tests 100% (32/32).

## What's Next (Immediate)
- Block B: Firebase Cloud Function proxy implementation
- Block B2: LangChain Python service deployment
- Update `CF_BASE_URL` configuration
- End-to-end integration testing

## Known Issues / Notes
- Indeterminate progress for uploads (acceptable for MVP).
- Security rules pending coordination; test media strictly in dev until policies land.
- 1 AuthViewModel test fails (pre-existing Android mocking issue, low priority).

