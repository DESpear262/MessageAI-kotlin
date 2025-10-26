# CASEVAC Workflow

## Overview
Autonomous multi-step medical evacuation flow coordinated by WorkManager.

## Steps
1) Generate 9-line MEDEVAC (AI)
2) Find nearest medical facility (FacilityService)
3) Create/Update CASEVAC mission (MissionService)
4) Increment casualties and mark complete

## Triggers
- Manual or AI intent detection

## Observability & Logging
- All AI HTTP calls include an `x-request-id` header that matches the envelope `requestId`.
- `CasevacWorker` emits JSON-structured Logcat lines with a stable `runId` per execution:
  - `casevac_start` { runId, chatId, attempt }
  - `casevac_template` { runId, chatId, latencyMs, ok }
  - `casevac_facility` { runId, chatId, lat, lon, facility }
  - `casevac_mission` { runId, chatId, missionId, incremented }
  - `casevac_complete` { runId, chatId, missionId }
  - Errors: `casevac_permission_error`, `casevac_failed`
- Notifications are posted at start and completion (see `CasevacNotifier`).

## Troubleshooting
- If AI calls time out or fail: filter Logcat by `x-request-id` captured in backend logs (CF `requestId`).
- If location is missing: look for `Location permission not granted` warnings; grant fine location.
- If mission updates are missing: check Firestore rules and `MissionService` logs.

## Reliability
- WorkManager uses network constraints and exponential backoff (30s base).
- Unique work name `casevac_<chatId>` to serialize per-chat flows.
- Safe location access: guarded permission checks and SecurityException handling.

