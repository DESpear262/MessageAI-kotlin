# Rubric Alignment

This document maps rubric items to artifacts in the repository for quick evaluation.

## Architecture
- Memory Bank: `memory-bank/*` (projectbrief, productContext, systemPatterns, techContext, activeContext, progress)
- Diagrams: `docs/product/messageai-architecture-v2.mermaid`
- Module layout: `app/src/main/java/com/messageai/tactical/modules/*`

## API Contracts
- Gateway contracts: `docs/api/AI_Gateway_Contracts.md`
- LangChain service: `langchain-service/README.md`
- Postman collection: `docs/postman/MessageAI-LangChain.postman_collection.json`

## Security
- CF gateway README (auth, HMAC): `firebase-functions/README.md`
- Security model overview: `docs/security/Security_Model.md`
- Keys server-side only; Android uses Firebase ID tokens

## Offline-first & Queue
- Room + WorkManager: `app/src/main/java/com/messageai/tactical/data/db/*`, `SendWorker`, `RouteExecutor`
- Logout isolation and cache clear: noted in `memory-bank/activeContext.md`

## AI Features
- Templates & SITREP: `modules/reporting/*`
- Threats & Geo: `modules/geo/*`, `ThreatAnalyzeWorker`
- Mission Tracker: `modules/missions/*` + `MissionBoard*`
- Assistant router + CASEVAC: `modules/ai/*`, `RouteExecutor`, `CasevacWorker`

## Testing & Evidence
- Testing guides: `docs/testing/*`
- Tickets & QC: `docs/reviews/*`, `docs/tickets/*`
- Evidence summary: `docs/testing/EVIDENCE.md`

## Observability
- Request IDs & logs: CF gateway, `MissionService` structured logs
- LangChain middleware auth/logging: `langchain-service/app/main.py`

## Deployment & Runbooks
- Root `README.md` (local dev + emulator)
- LangChain `README.md` (local/Docker)
- Functions `README.md` (emulator)


