# Active Context

## Current Focus
- Block E: Continue with optimistic send pipeline and message state transitions.

## Recent Changes
- Chat list added with FAB start-chat by email or screen name; user-friendly not-found.
- User lookup supports `email` and `displayNameLower` (registration stores it).
- Direct chat ensure sets `participantDetails`; self-chat named "Note to self".
- Chat screen added with Paging3 messages (newest at top) and send box.

## Next Steps
- Optimistic send state transitions; lastMessage sync on send.
- Top-of-list listener for newer messages and improved chat row previews.

## Decisions & Considerations
- Self-chat allowed; avoid duplicate receive by treating as single-participant flow at UI.
- Enforce/assume displayName uniqueness through `displayNameLower` for lookup.

## Risks
- Name collisions if two users share same normalized display name (can enforce uniqueness server-side later).

