# Active Context

## Current Focus
- Block J completed: Presence indicator dots (online/offline) on chat list and chat header.

## Recent Changes
- PresenceService reads RTDB `status/{uid}` and exposes a Flow<Boolean> for online state.
- Chat list shows a green dot for online, gray for offline per other participant.
- Chat header shows the same presence dot next to the title.

## Next Steps
- Optional: debounce/aggregate presence updates if needed; integrate typing indicator dots later.

