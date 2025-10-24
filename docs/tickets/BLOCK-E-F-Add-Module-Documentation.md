# TICKET: Add Module Documentation for Mission Tracker and CASEVAC

**Status:** 🟡 Backlog  
**Priority:** Medium  
**Type:** Documentation  
**Estimated Effort:** 3-4 hours  
**Assignee:** TBD  
**Created:** 2025-10-24

---

## Problem Summary

Blocks E (Mission Tracker) and F (CASEVAC Agent) lack comprehensive module-level documentation:

**Current State:**
- ❌ No `README.md` in `/modules/missions/`
- ❌ No `README.md` for CASEVAC workflow
- ❌ No `README.md` in `/modules/facility/`
- ⚠️ Inline comments exist but no high-level overview
- ⚠️ No usage examples for new developers

**Impact:**
- Medium: New developers struggle to understand modules
- Onboarding: Takes longer to understand CASEVAC workflow
- Maintainability: Hard to know what each module does

**Comparison:**
- ✅ Block A has excellent README (`/modules/ai/README.md`, 365 lines)
- ❌ Blocks E & F have minimal documentation

---

## Solution

Create comprehensive README files for all three modules following Block A's pattern.

### README Template Structure

1. **Overview** - Purpose and responsibilities
2. **Architecture** - Component diagram
3. **Components** - Key classes and their roles
4. **Usage Examples** - Code samples
5. **Integration Points** - Dependencies and interactions
6. **Configuration** - Constants and settings
7. **Firestore Schema** - Collection structure
8. **Testing** - How to test the module
9. **Known Limitations**
10. **Future Improvements**

---

## Implementation

### 1. Mission Tracker README

**File:** `app/src/main/java/com/messageai/tactical/modules/missions/README.md`

**Key Content:**
- Purpose: Real-time mission and task tracking via Firestore
- Architecture: MissionService + MissionBoardViewModel + MissionBoardScreen
- Firestore schema: `/missions/{id}/tasks/{taskId}`
- CRUD operations with suspend functions
- Real-time Flow observers
- CASEVAC integration (casualty tracking)
- AI integration via `AIService.extractTasks()`

**Code Examples:**
```kotlin
// Creating a mission
val missionId = missionService.createMission(
    Mission(
        chatId = "chat-123",
        title = "Secure Route Tampa",
        priority = 4,
        status = "in_progress"
    )
)

// Observing missions in real-time
missionService.observeMissions(chatId)
    .collect { missions ->
        updateUI(missions)
    }

// AI-based task extraction
val result = aiService.extractTasks(chatId, maxMessages = 100)
result.onSuccess { tasks ->
    tasks.forEach { taskMap ->
        missionService.addTask(missionId, parseTask(taskMap))
    }
}
```

---

### 2. CASEVAC Workflow README

**File:** `app/src/main/java/com/messageai/tactical/modules/ai/work/CASEVAC_README.md`

**Key Content:**
- Purpose: Autonomous multi-step medical evacuation workflow
- Architecture: WorkManager + AI + FacilityService + MissionService
- Workflow steps:
  1. Generate 9-line MEDEVAC template (AI)
  2. Find nearest medical facility
  3. Create mission with casualty tracking
  4. Update status and archive
- Triggers: Manual or AI-based intent detection
- Notifications: Start and completion alerts
- Persistence: Survives app restarts

**Code Examples:**
```kotlin
// Manual CASEVAC trigger
CasevacWorker.enqueue(
    context = context,
    chatId = "chat-123",
    messageId = "msg-456"
)

// AI-based intent detection
val intent = aiService.detectIntent(chatId, minMessages = 20)
intent.onSuccess { result ->
    if (result["intent"] == "casevac") {
        CasevacWorker.enqueue(context, chatId, null)
    }
}

// Monitor workflow with notifications
// Notifications automatically shown at start/completion
```

**Workflow Diagram:**
```
User/AI Detection
  ↓
CasevacWorker.enqueue()
  ↓
WorkManager (network constraint, retry logic)
  ↓
┌─────────────────────────────────────┐
│ Step 1: Generate 9-line (AI)       │
│ Step 2: Find nearest facility      │
│ Step 3: Create CASEVAC mission     │
│ Step 4: Increment casualties       │
│ Step 5: Mark done & archive        │
└─────────────────────────────────────┘
  ↓
Completion notification
  ↓
Mission appears in tracker
```

---

### 3. Facility Service README

**File:** `app/src/main/java/com/messageai/tactical/modules/facility/README.md`

**Key Content:**
- Purpose: Medical facility lookup with distance calculation
- Haversine formula for distance
- Availability filtering
- Capability tracking (surgery, trauma, medevac)

**Code Examples:**
```kotlin
// Find nearest available facility
val facility = facilityService.nearest(
    lat = 31.8633,
    lon = 64.2245,
    requireAvailable = true
)

facility?.let {
    println("${it.name} - ${it.capabilities.joinToString()}")
}
```

**Firestore Schema:**
```
/facilities/{facilityId}
  - name: string
  - lat: number
  - lon: number
  - capabilities: string[]     // ["surgery", "trauma", "medevac"]
  - available: boolean
```

---

## Acceptance Criteria

- [ ] README.md created for `/modules/missions/`
- [ ] CASEVAC_README.md created for workflow documentation
- [ ] README.md created for `/modules/facility/`
- [ ] All READMEs follow Block A template structure
- [ ] Architecture diagrams included (ASCII or mermaid)
- [ ] Usage examples provided with actual code
- [ ] Integration points documented
- [ ] Firestore schemas documented
- [ ] Known limitations listed
- [ ] Future improvements outlined
- [ ] Code review approved
- [ ] Peer review confirms completeness

---

## File Structure

```
app/src/main/java/com/messageai/tactical/modules/
├── missions/
│   ├── README.md                    ← NEW
│   ├── MissionModels.kt
│   ├── MissionService.kt
│   └── MissionModule.kt
├── facility/
│   ├── README.md                    ← NEW
│   ├── FacilityService.kt
│   └── FacilityModule.kt
└── ai/
    └── work/
        ├── CASEVAC_README.md        ← NEW
        ├── CasevacWorker.kt
        └── AIWorkflowWorker.kt
```

---

## Success Metrics

✅ **Definition of Done:**
1. Three README files created and committed
2. New developers can understand modules in < 20 minutes
3. All public APIs documented with examples
4. Integration points clearly explained
5. Workflow diagrams included
6. Firestore schemas documented
7. Peer review confirms completeness

---

## References

- Block A README: `app/src/main/java/com/messageai/tactical/modules/ai/README.md`
- QC Report: `docs/reviews/BLOCKS_E_F_QC_REPORT.md`
- Task Plan: `docs/product/messageai-sprint2-task-plan.md`

---

**Created by:** QC Agent (Blocks E & F Review)  
**Related Sprint:** Sprint 2 - AI Integration (Documentation)  
**Blocks:** E, F  
**Ticket ID:** BLOCK-E-F-001

