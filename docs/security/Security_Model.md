# Security Model

## Client → Cloud Functions
- Auth: Firebase ID token in `Authorization: Bearer <token>` (v1 routes)
- Limits: ~64KB payload cap, per‑UID token bucket to deter abuse
- Logs: Structured logs with request ID; no PII in logs

## Cloud Functions → LangChain
- HMAC headers:
  - `x-request-id`: UUID per request
  - `x-sig-ts`: epoch millis used by client when signing
  - `x-sig`: `HMAC_SHA256(LANGCHAIN_SHARED_SECRET, "{rid}.{ts}.{sha256(body)}")`
- Rejection policy: missing headers, invalid timestamp, expired age, or signature mismatch

## Key Management
- Secrets live server-side (Functions secrets, Cloud Run env); never shipped in Android app
- `OPENAI_API_KEY` is used only in Functions (embeddings on write)
- `LANGCHAIN_SHARED_SECRET` used only by Functions and LangChain

## Data Practices
- Minimize payloads (only fields needed by endpoint)
- No PII in app or backend logs; use `requestId` for tracing
- Android logout: stop listeners and clear Room to prevent cross-account leakage

## Device & Local Storage
- Android Keystore for auth tokens
- Room database for offline cache; cleared on logout
- Foreground-only notifications (MVP); can be extended post-MVP


