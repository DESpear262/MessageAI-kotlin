# Active Context

## Current Focus
- Block E: Continue with optimistic send pipeline and message state transitions.

## Recent Changes
- Implemented per-chat Firestore listener writing through to Room.
- Added deliveredBy updates on recipient receive; read receipt updater for fully visible messages.
- Added RTDB presence/typing service (status and typing paths with onDisconnect).

## Next Steps
- Wire optimistic send with WorkManager and update message states SENDING→SENT→DELIVERED→READ.
- Top-of-list listener for new messages and chat list lastMessage synchronization.

## Decisions & Considerations
- Mark read only for fully visible messages using viewport tracking; debounce writes.
- Use `deliveredBy` for delivery acknowledgment; `readBy` for fully viewed.
- RTDB presence/typing integrated; Firestore remains source for messages.

## Risks
- Read batching must be throttled to avoid excessive writes.
- Ensure security rules enforce self-only updates for delivered/read and typing.

