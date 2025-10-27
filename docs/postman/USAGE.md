# Postman – MessageAI

## Import
- Open Postman and import `MessageAI-LangChain.postman_collection.json` from `docs/postman/`.

## Environment
- Set `baseUrl` to your Cloud Functions emulator base, e.g.:
  - `http://127.0.0.1:5001/messageai-kotlin/us-central1/aiRouter/`
- For Android emulator testing, the app must use `http://10.0.2.2:5001/...` but Postman can keep `127.0.0.1`.

## Headers
- For v1 routes: `Authorization: Bearer <Firebase ID token>`
- Optional (required when shared secret enabled): `x-request-id`, `x-sig-ts`, `x-sig`

## Requests to try
- `POST {{baseUrl}}template/warnord`
- `POST {{baseUrl}}threats/extract`
- `POST {{baseUrl}}sitrep/summarize`
- `POST {{baseUrl}}assistant/route`

## Notes
- If the gateway rejects with 401, verify token and/or HMAC headers.
- Template endpoints may use longer timeouts; watch the console logs.
