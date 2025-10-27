# MessageAI Cloud Functions

## Overview

Cloud Functions power three concerns:
- Push notifications for new chat/group messages.
- On-write RAG embeddings for messages (chunk → embed → `chunks/{seq}`).
- Secure AI proxy gateway that fronts the LangChain FastAPI service.

## HTTP Entrypoints

- `aiRouter` (secure, versioned): `/v1/*`
  - Requires Firebase ID token (Authorization: Bearer <token>)
  - Adds HMAC headers (`x-request-id`, `x-sig-ts`, `x-sig`) to LangChain calls
  - Payload cap ~64KB; per‑UID token bucket; structured logs
- `aiRouterSimple?path=...` (dev/testing)
  - No auth, minimal validation; for local development only
- `openaiProxy` (legacy)

## Firestore Triggers

- `sendPushNotification`: `chats/{chatId}/messages/{messageId}` → multicast FCM
- `sendGroupPushNotification`: `groups/{groupId}/messages/{messageId}` → multicast FCM
- `embedOnMessageWrite`: `chats/{chatId}/messages/{messageId}` → chunk text (~700 chars) and store embeddings under `chunks/{seq}`

## Configuration (Params/Secrets)

- `LANGCHAIN_BASE_URL`: Base URL to LangChain service (e.g., `http://127.0.0.1:8000`)
- `OPENAI_API_KEY`: Used by `embedOnMessageWrite` for text embedding

## Emulator

```bash
npx --yes firebase-tools@latest emulators:start --only functions
```

Android emulator cannot reach `127.0.0.1` on the host; use `10.0.2.2` for URLs the app calls.

## HMAC headers to LangChain

Forwarded headers:
- `x-request-id`: UUID per request (from client)
- `x-sig-ts`: epoch millis the client used to sign
- `x-sig`: HMAC-SHA256 over `{requestId}.{ts}.{sha256(body)}` with `LANGCHAIN_SHARED_SECRET`

LangChain rejects requests with missing headers, bad timestamps, excessive age, or signature mismatch.

## Notes

- Do not expose API keys to the client. Only CF reads secrets.
- Observe logs for success/failure counts when sending FCM multicast.
- Keep timeouts aligned with LangChain endpoints (slow endpoints like templates use longer timeouts).


