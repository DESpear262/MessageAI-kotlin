# Active Context

## Current Focus
- Block I completed: Offline support & send queue.

## Recent Changes
- Firestore offline persistence enabled.
- SendWorker with constraints/backoff; idempotent merge writes.
- Optimistic local insert; enqueue per message; re-scan queue on app start.
- State transitions SENDING→SENT; lastMessage updated on send.

## Next Steps
- Proceed to next block (F: Groups, G: Media, or H: Notifications) per plan.

## Risks
- Permanent failure surfacing UX (FAILED + retry) can be improved later.

