# Active Context

## Current Focus
- Sprint 2 AI Integration: ✅ ALL BLOCKS COMPLETE & QC APPROVED (A, B, B2, C, D, E, F)
  - New: AI Buddy tab (SmartToy) as the single entry point for AI features
  - Buddy mirrors all AI actions into an AI-only chat (hidden from Chats list)
  - CASEVAC routed to server workflow; mission created server-side and echoed to Buddy
  - App routes all AI calls via Functions → LangChain; backend is swappable
  - Non-determinism rule: ALL tool selection is done by LLM (assistant/route). No regex/keyword routing or defaults in app.
  - Outputs tab shows a report selector; nothing runs until user chooses explicitly.
  - Block A: AI Core module ✅ COMPLETE & QC APPROVED
  - Block B: Firebase Functions AI gateway ✅ COMPLETE & QC APPROVED
  - Block B2: LangChain Service ✅ COMPLETE & QC APPROVED
  - Block C: Geo Intelligence ✅ COMPLETE & QC APPROVED
  - Block D: Reporting ✅ COMPLETE & QC APPROVED
  - Block E: Mission Tracker & Dashboard ✅ COMPLETE & QC APPROVED
  - Block F: CASEVAC Agent Workflow ✅ COMPLETE & QC APPROVED

## Recent Changes
- Fixed cross-account data leakage on Android: when User A logs out and User B logs in on the same device, B could previously see A's chats from the local Room cache.
  - Changes:
    - Filter chat list Flow by current Firebase UID in `ChatListViewModel`.
    - Stop Firestore listeners on chat list dispose and on logout.
    - On logout, run a background task to stop listeners, sign out, and clear Room with `db.clearAllTables()`; only then update UI auth state.
  - Result: No stale chats leak between accounts; logout no longer crashes.
- **QC completed for Blocks E & F:**
  - Comprehensive code review report created (`docs/reviews/BLOCKS_E_F_QC_REPORT.md`)
  - Block E: MissionService with real-time Firestore Flow, MissionBoardScreen UI, AIService.extractTasks()
  - Block F: CasevacWorker with multi-step workflow, FacilityService, AIService.detectIntent/runWorkflow()
  - Both blocks properly integrate with AIService and LangChainAdapter
  - WorkManager integration with retry/backoff for CASEVAC workflow
  - Status: ✅ APPROVED FOR MVP DEPLOYMENT
- **QC completed for Blocks C & D:**
  - Comprehensive code review report created (`docs/reviews/BLOCKS_C_D_QC_REPORT.md`)
  - Block C: GeoService with AI integration, threat extraction, geofencing
  - Block D: ReportService with SITREP & NATO templates (WARNORD, OPORD, FRAGO)
  - Both blocks properly integrate with AIService and LangChainAdapter
  - Status: ✅ APPROVED FOR MVP DEPLOYMENT
- LangChain FastAPI service with endpoints: template, threats, sitrep, intent, workflow
- CF AI Gateway with three-tier architecture:
  - `aiRouter` at `/v1/*` (production-grade with full security)
  - `aiRouterSimple?path=...` (development/testing)
  - `openaiProxy` (legacy support)

## Ongoing Enhancements (post-QC)
- RAG Embedding Pipeline (DEV complete):
  - Cloud Functions Firestore trigger embeds new messages on write, chunks at ~700 chars, and stores vectors at `chats/{chatId}/messages/{messageId}/chunks/{seq}`.
  - LangChain service prefers precomputed chunk vectors for RAG; only embeds the query at fill time.
  - Added `/rag/warm` endpoint to backfill embeddings for existing chats; Android helper `RagBackfill.run(context)` exists but is intentionally isolated (no UI entry).
  - AI Buddy excludes the control chat from candidate lists; server also filters control-chat names before LLM selection.
- Timeouts and stability:
  - Cloud Function `aiRouter` timeouts increased (DEV): fast=20s, slow=60s; template endpoints use slow.
  - Android OkHttp timeouts increased (connect=15s, read=45s, write=30s) to avoid client-side read timeouts during LLM fills.

## How FRAGO/OPORD/WARNORD Fill Now Works
1) App sends prompt + candidate chats to `assistant/route`; LLM selects tool and (server) infers chat when needed (Buddy chat excluded).
2) App executes template tool; CF proxies to LangChain with 60s timeout.
3) LangChain loads template and builds RAG context using precomputed chunk embeddings; performs only a query embed; fills placeholders via LLM; returns markdown.

## Next Steps
- Deploy LangChain service to Cloud Run/GKE (private network)
- Configure production environment variables:
  - `LANGCHAIN_BASE_URL` in Cloud Functions
  - `LANGCHAIN_SHARED_SECRET` in Cloud Functions secrets
  - `ALLOWED_ORIGINS` for CORS
  - `OPENAI_API_KEY` in LangChain service
  - `FIRESTORE_PROJECT_ID` in LangChain service
- Update Android `BuildConfig.CF_BASE_URL` to production Firebase Function URL
- Conduct end-to-end smoke testing (Android → CF → LangChain → response)
 - Verify AI Buddy chat persistence end-to-end on cold-install; confirm both directions render in standard ChatScreen.

## Risks
- In-memory rate limiting resets on cold start (acceptable for MVP)
- In-memory RAG cache (no persistence between requests)
- All limitations documented and acceptable for MVP

