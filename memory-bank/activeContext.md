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

### Threat pipeline (2025-10-26)
- Proactive “gate then escalate” design:
  - `/assistant/gate` uses gpt-4.1-nano (triple vote) to decide escalation; returns `{escalate}` and logs votes.
  - On escalate=true, `RouteExecutor` calls `/assistant/route` (4o-mini) and then executes the chosen tool.
- Threat extraction is now strict single-message, no chat history:
  - `/threats/extract` evaluates only the provided message (`payload.message` or `messages[triggerMessageId]` or `prompt`).
  - Attaches `sourceMsgId/sourceMsgText`; uses `currentLocation` for offsets when needed.
  - Structured logging added: resolve → prompt → raw → parse → fallback → result.
- Android execution & persistence:
  - `RouteExecutor` executes `threats/extract` with the triggering message and persists threats immediately to Firestore `threats`.
  - Falls back to device location when no absolute coords provided.

## Next Steps
- Optionally strip codeblock wrappers (```json) server-side before JSON parsing in `/threats/extract`.
- Add stored `rationale` for auditability of threat decisions.
- Tighten unit tests for gate, route, and single-message tool contracts.

### Mission Planner Observability (2025-10-26)
- `MissionService` now emits structured logs for mission/task create/update, archive decisions, and Flow emissions/errors.
- `MissionBoardViewModel` logs chat selection and status updates.
 - `MissionTasksViewModel` logs missionId selection and each tasks emission.
 - Added raw-output logging to LangChain `/missions/plan` and tightened prompt + fence stripping to ensure strict JSON.

### Missions UX & Access (2025-10-26)
- Missions tab now uses a global feed: `MissionService.observeMissionsGlobal()` (all signed-in users can see missions for MVP).
- Mission rows are clickable; new `MissionTasksScreen` shows the mission's tasks list.
- Navigation: `mission/{missionId}?chatId={chatId}` route added in `AppRoot`.
- Firestore rules (MVP) updated so any authenticated user can read/write `missions/*` and `missions/*/tasks/*`.
- Firestore composite indexes created:
  - `missions`: archived ASC, updatedAt DESC (global feed)
  - (optional per-chat view) chatId ASC, archived ASC, updatedAt DESC

## Risks
- Gate escalations rely on upstream model; triple-vote mitigates flukes but adds 2× calls (still cheap).
- Location permissions may be missing; persistence uses fallback rules and guards.

