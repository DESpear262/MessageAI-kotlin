# Product Context

## Why
Reliable tactical communication under constrained connectivity. Prioritize reliability, speed, and offline-first UX.

## Users & Scenarios
- Small teams coordinating in the field; intermittent/poor networks.
- Needs: Low-latency chat, clear delivery/read states, resilience to loss/restarts.

## UX Goals
- Messages feel instant (optimistic update), status clarity (sending/sent/delivered/read), accurate timestamps.
- Presence and typing indicators to set expectations.
- Seamless offline: read history offline; queued sends auto-flush on reconnect.

## Core Stories (condensed)
- Auth: register/login/reset; profile with name/avatar.
- Direct chat: realtime send/receive; message states; presence/typing; persisted history.
- Groups: 3+ members; named chats; sender attribution; per-user read receipts.
- Media: pick/preview/send images; inline display; cached locally.
- Notifications: foreground notifications deep-linking to the relevant chat.

## Constraints
- MVP timeline; Android-first; Firebase backend; local Room; DI with Hilt; queue via WorkManager.

