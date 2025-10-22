# Active Context

## Current Focus
- Block I: Offline support & sync/queue (WorkManager-based send pipeline).

## Recent Changes
- Firestore offline persistence enabled in DI.
- SendWorker added with exponential backoff (2s → 5m), network + battery constraints, idempotent merge writes.
- Chat send now enqueues background work and inserts local SENDING entity.

## Next Steps
- Re-scan queue on app start to enqueue missing work; transition states SENDING→SENT and delivered/read.
- Network callback kick (optional) to hasten retries; lastMessage updates on send.

## Risks
- Ensure retry limits and error surfacing for permanent failures.

