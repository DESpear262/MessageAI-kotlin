# Active Context

## Current Focus
- Block F: Group chat – unified chat list, group creation, sender attribution.

## Recent Changes
- Unified chat list (direct + group) from single `chats` collection ordered by `updatedAt`.
- Added `name` to `ChatDoc`; Mapper prefers explicit name else derives from participants.
- Group creation API in `ChatService` (random id), rename API; LWW `updatedAt`.
- Chat create UI now supports multi-select add/remove and Create button; auto-names from member names.
- Chat bubbles show sender name and an avatar placeholder for non-self messages (every bubble).

## Next Steps
- Wire delivered/read receipts for groups consistent with direct chats (wait for 1:1 agent completion, then integrate).
- Ensure participant name cache loads on chat open.

## Risks
- Permanent failure surfacing UX (FAILED + retry) can be improved later.

