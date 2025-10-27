# MessageAI LangChain Service (FastAPI)

Provider-agnostic Python microservice backing AI features. Called via Firebase Cloud Function proxy. Implements unversioned endpoints per Sprint 2 PRD.

## Endpoints
- POST /template/generate
- POST /threats/extract
- POST /sitrep/summarize
- POST /intent/casevac/detect
- POST /workflow/casevac/run
- GET /healthz

Request envelope:
```json
{
  "requestId": "uuid",
  "context": { "chatId": "..." },
  "payload": { }
}
```

Response envelope:
```json
{ "requestId": "uuid", "status": "ok", "data": { ... } }
```

## Run locally
```bash
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8080
```

Env vars:
- OPENAI_API_KEY (optional; mock mode if absent)
- FIRESTORE_PROJECT_ID (optional; uses default credentials if provided)
- GOOGLE_APPLICATION_CREDENTIALS (optional; service account JSON path)

## Docker
```bash
docker build -t messageai-langchain:dev ./langchain-service
docker run -p 8080:8080 --env-file .env messageai-langchain:dev
```

## Notes
- Templates map to Input file templates in repo. Contracts mirror those field names.
- Current implementation stubs out RAG and returns minimal, schema-valid data.
- Integrate with CF proxy by setting the base URL in Android `BuildConfig.CF_BASE_URL` once the CF is deployed and `LANGCHAIN_BASE_URL` is configured.

## HMAC Verification (from Cloud Functions)

Incoming requests may include HMAC headers from the gateway:
- `x-request-id` (UUID)
- `x-sig-ts` (epoch millis)
- `x-sig` (hex HMAC-SHA256 over `{requestId}.{ts}.{sha256(body)}` using `LANGCHAIN_SHARED_SECRET`)

The service rejects requests when headers are missing, timestamps are invalid/stale, or the signature mismatches. See `app/main.py` middleware for details.