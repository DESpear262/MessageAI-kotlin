# Active Context

## Current Focus
- Block A: AI Core module ✅ COMPLETE
- Block B: Functions AI gateway ✅ COMPLETE (local + secure routers)
- Block B2: LangChain Service running locally (uvicorn) and proxied via CF

## Recent Changes
- LangChain FastAPI service under `langchain-service/` with endpoints: template, threats, sitrep, intent, workflow
- CF AI Gateway:
  - `aiRouter` at `/v1/*` (ID token required, HMAC signing, rate limits, 64KB cap, timeouts)
  - `aiRouterSimple?path=...` for quick local testing
  - Defaults to local `http://127.0.0.1:8000` when `LANGCHAIN_BASE_URL` is unset
- README updated with emulator host (`10.0.2.2`) guidance and curl test commands

## Next Steps
- Optional: deploy LangChain to Cloud Run; set `LANGCHAIN_BASE_URL`
- Android dev: set `CF_BASE_URL` to `http://10.0.2.2:5001/messageai-kotlin/us-central1/` for emulator testing
- Add per-endpoint data classes and optional AI response cache

## Risks
- Cold start/latency if Cloud Run min instances not configured
- Schema drift between CF proxy and service; covered by Postman tests

