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

## Notes
- Resilient with retries; permission-checked location access; structured logging for observability.

