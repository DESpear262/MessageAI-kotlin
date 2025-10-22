# Active Context

## Current Focus
- Preparing Block I: Offline support & sync/queue (optimistic send via WorkManager, retries, network handling).

## Recent Changes
- 1:1 chat list with real-time subscription and navigation.
- Chat title now shows other participant’s screen name (Room-backed).
- Chat creation by email/screen name; self-chat labeled "Note to self".
- Chat screen: newest-at-top, styled bubbles (mine right/blue, other left/gray), input bar, back button.
- Landing page top bar with Logout.

## Next Steps
- Implement send queue with WorkManager (OneTime + backoff), mark states SENDING→SENT→DELIVERED→READ.
- Firestore offline persistence toggle; network monitoring for graceful reconnects.
- Ensure lastMessage updates on send and list previews are consistent.

## Decisions & Considerations
- Room is source of truth; Firestore listeners write-through.
- Read receipts only for fully visible messages; deliveredBy on receive.

## Risks
- Write volume for read receipts; keep debounced.
- Index requirements for combined queries as features increase.

