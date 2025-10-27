# AI Data Flow (Client → CF → LangChain)

```mermaid
flowchart LR
  App[Android App] -->|Bearer ID token| CF[Firebase Functions /v1]
  CF -->|HMAC: x-request-id,x-sig-ts,x-sig| LC[LangChain FastAPI]
  LC --> FS[Firestore (RAG chunks)]
  LC --> OAI[Provider(s)]
  FS --> LC
  LC --> CF
  CF --> App
```

- App authenticates with Firebase; sends only minimal payloads.
- CF verifies token, signs request to LangChain (HMAC), enforces limits.
- LangChain validates HMAC, performs RAG/LLM work, returns envelope.
- Request IDs propagate end-to-end for observability.
