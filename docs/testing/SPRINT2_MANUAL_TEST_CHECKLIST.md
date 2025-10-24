# Sprint 2 Manual Testing Checklist
**Sprint:** Sprint 2 – AI Integration  
**Date:** October 24, 2025  
**Blocks:** A, B, B2, C, D, E, F  
**Status:** Ready for Testing

---

## Pre-Test Setup

### Environment Configuration
- [ ] Android device/emulator with API 26+ running
- [ ] Firebase Dev project configured (`app/src/dev/google-services.json`)
- [ ] LangChain service running (local or deployed)
- [ ] Firebase Functions deployed (dev environment)
- [ ] Test user account created in Firebase Auth
- [ ] Location permissions granted (for Blocks C & F)
- [ ] Notification permissions granted (Android 13+)
- [ ] Network connectivity available

### Test Data Preparation
- [ ] Create test chat with ID (note chatId for testing)
- [ ] Seed test messages in Firestore (20-50 messages)
- [ ] Create test facilities in Firestore `/facilities` collection:
  ```json
  {
    "name": "Camp Bastion Medical",
    "lat": 31.8633,
    "lon": 64.2245,
    "capabilities": ["surgery", "trauma", "medevac"],
    "available": true
  }
  ```
- [ ] Clear existing missions/threats for clean test

### Seeded Chats (Dev Data)
- OPORD WOLVERINE (Captain Parker → Lieutenant Davis): Company-level OPORD for generating a platoon-level WARNO. Includes UAS/mortar PIR, OBJ HAWK 38T MN 2385 5650, LD 242000Z, CMD NET 30.000 MHz.
- FRAGO 01 to WOLVERINE (Captain Parker → Lieutenant Davis): Addendum adjusting LD and CP 2A; use for FRAGO generation.
- Patrol Chat A – AA PINE (Lieutenant Davis ↔ PSG Ortiz): SITREP-rich patrol with HLZ at MN 2349 5645, SMOKE GREEN, CMD NET 30.000 MHz; good for SITREP and 9-line pre-brief inputs (no injury).
- Patrol Chat B – Mill Road, MOPP (Lieutenant Davis ↔ PSG Ortiz): MOPP discipline implied; HLZ at MN 2415 5628, SMOKE YELLOW; nationality mix (US + linguists). Use for SITREP and 9-line variant with NBC context implied.
- OPORD ORYX (Captain Kim → Lieutenant Davis): Company-level OPORD for generating a full platoon-level OPORD and mission planner verification; HAMLETS SOUTH, BP ALPHA MN 2235 5568.
- HLZ REED Contingency (Lieutenant Davis ↔ PSG Ortiz): River bend tertiary HLZ, panels marking; used to test extraction of alternative HLZs and irrelevant chatter.
- Logistics Note (Captain Parker → Lieutenant Davis): LOGPAC timing and smoke guidance; extra chatter/noise for extraction robustness.
- Pre-LD Comms (Lieutenant Davis ↔ PSG Ortiz): Isolation BP ALPHA set, school field HLZ at MN 2225 5566; contingency MEDEVAC pre-brief elements.

Finding a seeded chat in app/Firestore
- In app: open chat by participants (emails: cpt.t.parker@dev.mil, cpt.s.kim@dev.mil, lt.j.davis@dev.mil, psg.m.ortiz@dev.mil).
- In Firestore: search `chats` where `participantDetails` names match the users above or filter by `lastMessage.text` with phrases like "OPORD WOLVERINE", "FRAGO 01", "MOPP", "HLZ".

### Build & Deploy
```bash
# Build Android app
./gradlew :app:assembleDevDebug

# Install on device
adb install app/build/outputs/apk/dev/debug/app-dev-debug.apk

# Verify Firebase Functions deployed
firebase deploy --only functions

# Verify LangChain service running
curl http://localhost:8000/health
```

---

## Block A: AI Core Module

### Test 1: Template Generation
**Objective:** Verify AI can generate tactical templates

**Steps:**
1. Open app and navigate to chat
2. Trigger template generation (via code or test UI)
3. Call `AIService.generateTemplate(chatId, "OPORD", 50)`

**Expected:**
- ✅ Request completes in 2-5 seconds
- ✅ Returns structured data with template fields
- ✅ No crashes or errors in logcat

**Verification:**
```bash
# Check logcat for AI service calls
adb logcat | grep "AIService"
```

**Pass/Fail:** ⬜

---

### Test 2: RAG Context Building
**Objective:** Verify context builder fetches recent messages

**Steps:**
1. Ensure chat has 30+ messages
2. Call `RagContextBuilder.build(WindowSpec(chatId, maxMessages=20))`
3. Verify returned list size

**Expected:**
- ✅ Returns exactly 20 messages (or fewer if chat has less)
- ✅ Messages ordered by timestamp (newest first)
- ✅ Each message has `id`, `senderId`, `timestamp`, `text` fields

**Pass/Fail:** ⬜

---

### Test 3: Offline Queue (WorkManager)
**Objective:** Verify AI tasks queue when offline

**Steps:**
1. Turn on airplane mode
2. Trigger AI operation (e.g., template generation)
3. Verify WorkManager job queued
4. Turn off airplane mode
5. Wait for job execution

**Expected:**
- ✅ Job enqueued while offline
- ✅ Job executes automatically when online
- ✅ No data loss

**Verification:**
```bash
# Check WorkManager status
adb shell dumpsys jobscheduler | grep AIWorkflowWorker
```

**Pass/Fail:** ⬜

---

### Test 4: Provider Swapping
**Objective:** Verify LocalProvider works for offline testing

**Steps:**
1. Configure app to use LocalProvider (in AIModule)
2. Call any AI method
3. Verify mock responses returned

**Expected:**
- ✅ Returns immediately (<100ms)
- ✅ Mock data structure matches expected format
- ✅ No network calls made

**Pass/Fail:** ⬜

---

## Block B: Firebase Functions Proxy

### Test 5: AI Router (Production Path)
**Objective:** Verify production proxy routes AI calls correctly

**Steps:**
1. Configure app with Firebase Function URL
2. Generate Firebase ID token
3. Call `/v1/template/generate` via app
4. Monitor Cloud Functions logs

**Expected:**
- ✅ Request authenticated (ID token verified)
- ✅ HMAC signature added
- ✅ Request forwarded to LangChain service
- ✅ Response returned to app
- ✅ Latency < 10s (2-5s typical)

**Verification:**
```bash
# Check Firebase Functions logs
firebase functions:log --only aiRouter
```

**Pass/Fail:** ⬜

---

### Test 6: Rate Limiting
**Objective:** Verify rate limiting prevents abuse

**Steps:**
1. Make 10 rapid requests to AI endpoint (within 1 second)
2. Observe responses

**Expected:**
- ✅ First 10 requests succeed (burst allowance)
- ✅ 11th request returns `429 Rate limit exceeded`
- ✅ Wait 60 seconds, requests resume

**Pass/Fail:** ⬜

---

### Test 7: Authentication Failure
**Objective:** Verify unauthenticated requests rejected

**Steps:**
1. Send request without Firebase ID token
2. Send request with invalid/expired token

**Expected:**
- ✅ Returns `401 Unauthorized`
- ✅ No request forwarded to LangChain

**Pass/Fail:** ⬜

---

### Test 8: Payload Size Limit
**Objective:** Verify large payloads rejected

**Steps:**
1. Send request with >64KB payload
2. Observe response

**Expected:**
- ✅ Returns `413 Payload too large`
- ✅ No request forwarded to LangChain

**Pass/Fail:** ⬜

---

## Block B2: LangChain Service

### Test 9: Health Check
**Objective:** Verify service is running

**Steps:**
1. Send GET request to `/health`

**Expected:**
```json
{
  "status": "ok",
  "service": "messageai-langchain",
  "timestamp": 1729785600
}
```

**Pass/Fail:** ⬜

---

### Test 10: Template Generation Endpoint
**Objective:** Verify `/template/generate` returns markdown

**Steps:**
1. POST to `/template/generate`:
```json
{
  "requestId": "test-123",
  "context": {"chatId": "chat-test"},
  "payload": {"type": "OPORD", "contextText": "Brief context"}
}
```

**Expected:**
- ✅ Returns status 200
- ✅ Response has `data.content` with markdown string
- ✅ `data.format` = "markdown"

**Pass/Fail:** ⬜

---

### Test 11: Threats Extraction
**Objective:** Verify `/threats/extract` identifies threats

**Steps:**
1. POST to `/threats/extract`:
```json
{
  "requestId": "test-124",
  "context": {"chatId": "chat-test"},
  "payload": {"contextText": "Enemy contact at grid 12345678. IED suspected on route Hawk."}
}
```

**Expected:**
- ✅ Returns list of threats
- ✅ Each threat has `summary`, `severity`, `confidence`, `geo`
- ✅ Severity 1-5, confidence 0.0-1.0

Seeded chat guidance
- Use Patrol Chat A (Davis ↔ Ortiz, AA PINE). Contains mentions of possible scouts on MSR GREEN and UAS noise; good to validate recon/IDF-related threat extraction.

**Pass/Fail:** ⬜

---

### Test 12: SITREP Summarization
**Objective:** Verify `/sitrep/summarize` generates summary

**Steps:**
1. POST to `/sitrep/summarize`:
```json
{
  "requestId": "test-125",
  "context": {"chatId": "chat-test"},
  "payload": {"timeWindow": "6h"}
}
```

**Expected:**
- ✅ Returns markdown SITREP
- ✅ Contains sections (summary, activities, etc.)

Seeded chat guidance
- Prefer Patrol Chat A for a baseline SITREP (no NBC). Alternate: Patrol Chat B to verify NBC-adjacent operational impacts (MOPP 4, slower movement) are captured without explicit contamination claims.

**Pass/Fail:** ⬜

---

## Block C: Geolocation Intelligence

### Test 13: Threat Analysis (AI Integration)
**Objective:** Verify threats extracted from chat messages

**Steps:**
1. Seed chat with threat-related messages:
   - "Enemy patrol spotted at checkpoint Alpha"
   - "IED reported on Route Tampa near grid 123456"
2. Call `GeoService.analyzeChatThreats(chatId, 100)`
3. Wait for completion callback
4. Check Firestore `/threats` collection

**Expected:**
- ✅ Callback invoked with count > 0
- ✅ Threats persisted to Firestore
- ✅ Each threat has `summary`, `severity`, `geo`, `ts`
- ✅ Notification shown (optional)

**Pass/Fail:** ⬜

---

### Test 14: Geofence Alerts
**Objective:** Verify alerts when entering threat zone

**Steps:**
1. Create test threat near current location:
```json
{
  "summary": "Test threat zone",
  "severity": 4,
  "lat": <current_lat>,
  "lon": <current_lon>,
  "radiusM": 500,
  "ts": <now>
}
```
2. Call `GeoService.checkGeofenceEnter(lat, lon)`

**Expected:**
- ✅ Alert callback triggered
- ✅ High-priority notification shown
- ✅ Notification contains threat summary

**Pass/Fail:** ⬜

---

### Test 15: Signal Loss Alert
**Objective:** Verify alert after consecutive heartbeat misses

**Steps:**
1. Call `GeoService.alertSignalLossIfNeeded(1)` - no alert
2. Call `GeoService.alertSignalLossIfNeeded(2)` - should alert

**Expected:**
- ✅ No alert on first miss
- ✅ High-priority notification on second consecutive miss
- ✅ Notification title: "Signal Loss Detected"

**Pass/Fail:** ⬜

---

### Test 16: Threat Proximity Search
**Objective:** Verify nearby threats filtered correctly

**Steps:**
1. Create threats at various distances:
   - Threat A: 5 miles away, severity 5
   - Threat B: 10 miles away, severity 3
   - Threat C: 100 miles away, severity 5
   - Threat D: 5 miles away, 9 hours old (expired)
2. Call `GeoService.summarizeThreatsNear(lat, lon, maxMiles=50, limit=10)`

**Expected:**
- ✅ Returns Threat A and B only (within range, < 8 hours old)
- ✅ Threat C excluded (too far)
- ✅ Threat D excluded (expired)
- ✅ Sorted by severity (A before B)

**Pass/Fail:** ⬜

---

## Block D: Template Generation & SITREP

### Test 17: SITREP Generation
**Objective:** Verify SITREP markdown generated

**Steps:**
1. Navigate to reporting screen
2. Select "Generate SITREP"
3. Choose time window: 6h
4. Wait for generation

**Expected:**
- ✅ Loading indicator shown
- ✅ Markdown preview appears in 2-8 seconds
- ✅ Markdown contains `# SITREP` header
- ✅ Content includes summary of chat activity

**Pass/Fail:** ⬜

---

### Test 18: NATO Template Generation
**Objective:** Verify WARNORD/OPORD/FRAGO templates load

**Steps:**
1. Navigate to reporting screen
2. Select "Generate WARNORD"
3. Wait for template

**Expected:**
- ✅ Markdown template loads from repository
- ✅ Contains `# WARNING ORDER` header
- ✅ Sections: Situation, Mission, Execution, etc.

**Repeat for:**
- [ ] OPORD (Operations Order)
- [ ] FRAGO (Fragmentary Order)

Seeded chat guidance
- WARNORD: Use OPORD WOLVERINE chat (Parker → Davis) as context to derive a platoon-level WARNO.
- OPORD: Use OPORD ORYX chat (Kim → Davis) to generate a platoon-level OPORD (isolation mission, BP ALPHA MN 2235 5568).
- FRAGO: Use FRAGO 01 to WOLVERINE (Parker → Davis) to produce a FRAGO reflecting LD/CP 2A changes.

**Pass/Fail:** ⬜

---

### Test 19: Report Sharing
**Objective:** Verify markdown export via FileProvider

**Steps:**
1. Generate any report (SITREP or template)
2. Tap "Share" button (FAB)
3. Select share target (email, messaging, etc.)

**Expected:**
- ✅ Android share sheet appears
- ✅ File accessible to target app
- ✅ Markdown formatted correctly
- ✅ Filename includes timestamp

**Pass/Fail:** ⬜

---

## Block E: Mission Tracker & Dashboard

### Test 20: Mission Creation (Manual)
**Objective:** Verify missions can be created and displayed

**Steps:**
1. Directly create mission in Firestore or via service:
```kotlin
missionService.createMission(
    Mission(
        chatId = "chat-test",
        title = "Test Mission Alpha",
        description = "Verify mission tracker",
        status = "open",
        priority = 3
    )
)
```
2. Open MissionBoardScreen

**Expected:**
- ✅ Mission appears in list within 2 seconds
- ✅ Title and description displayed correctly
- ✅ Status shows "open"

**Pass/Fail:** ⬜

---

### Test 21: Real-Time Mission Updates
**Objective:** Verify dashboard updates in real-time

**Steps:**
1. Open MissionBoardScreen
2. From another device/Firestore console, update mission status to "in_progress"
3. Observe UI

**Expected:**
- ✅ Status updates within 2 seconds (no refresh needed)
- ✅ UI reflects new status

**Pass/Fail:** ⬜

---

### Test 22: Status Change via UI
**Objective:** Verify status dropdown updates Firestore

**Steps:**
1. Open MissionBoardScreen
2. Tap status button on a mission
3. Select "done" from dropdown
4. Check Firestore

**Expected:**
- ✅ Dropdown appears with options: open, in_progress, done
- ✅ Selection updates Firestore
- ✅ Mission archived if all tasks done (auto-archive)

**Pass/Fail:** ⬜

---

### Test 23: AI Task Extraction (Infrastructure)
**Objective:** Verify extractTasks() method exists and calls LangChain

**Steps:**
1. Call `AIService.extractTasks(chatId, 100)` programmatically
2. Monitor network traffic

**Expected:**
- ✅ Request sent to LangChain `/tasks/extract`
- ✅ Returns Result.success with list (may be empty for MVP stub)
- ✅ No crashes

**Pass/Fail:** ⬜

---

## Block F: CASEVAC Agent Workflow

### Test 24: CASEVAC Workflow (Manual Trigger)
**Objective:** Verify autonomous multi-step CASEVAC workflow

**Steps:**
1. Ensure location permissions granted
2. Ensure test facilities in Firestore
3. Manually trigger CASEVAC:
```kotlin
CasevacWorker.enqueue(context, chatId = "chat-test", messageId = "msg-123")
```
4. Monitor notifications and Firestore

Seeded chat guidance
- Use Patrol Chat A for baseline CASEVAC (HLZ MN 2349 5645, SMOKE GREEN, US Mil only). Add the actual injury message yourself to trigger intent.
- Use Patrol Chat B (MOPP) to validate NBC-adjacent context handling (HLZ MN 2415 5628, SMOKE YELLOW, mixed nationality incl. linguists).

**Expected Sequence:**
- ✅ Step 1: Start notification shown ("CASEVAC started")
- ✅ Step 2: MEDEVAC template generated (AI call)
- ✅ Step 3: Nearest facility found (Firestore query)
- ✅ Step 4: Mission created in `/missions` with title "CASEVAC"
- ✅ Step 5: Casualty count incremented
- ✅ Step 6: Mission marked "done" and archived
- ✅ Step 7: Completion notification shown ("CASEVAC completed - [facility name]")
- ✅ Total time: 3-10 seconds

**Verification:**
```bash
# Check WorkManager execution
adb shell dumpsys jobscheduler | grep CasevacWorker

# Check logcat
adb logcat | grep -E "CasevacWorker|CasevacNotifier"
```

**Pass/Fail:** ⬜

---

### Test 25: CASEVAC Intent Detection (Proactive AI)

**Objective:** Verify AI can detect CASEVAC intent from chat messages

#### Should TRIGGER CASEVAC:

**Test Messages (send in chat, then call detectIntent):**

1. **Explicit MEDEVAC Request:**
   - ✅ "Need MEDEVAC at grid 12345678"
   - ✅ "Request immediate medical evacuation, 2 casualties"
   - ✅ "9-line MEDEVAC requested"

2. **Urgent Medical Emergency:**
   - ✅ "Soldier down, heavy bleeding, need medic NOW"
   - ✅ "Multiple casualties from IED, urgent evacuation needed"
   - ✅ "Man down with gunshot wound to chest, critical"

3. **Casualty with Evacuation Context:**
   - ✅ "Casualty requires higher level of care, prep for transport"
   - ✅ "Patient stable but needs hospital, arrange transport"
   - ✅ "WIA needs surgical facility ASAP"

**Expected:**
- ✅ `AIService.detectIntent(chatId, 20)` returns `intent: "casevac"`
- ✅ Confidence ≥ 0.7 (70%+)
- ✅ Triggers list contains relevant keywords

Seeded chat guidance
- Send the trigger messages in Patrol Chat A or B. A = non-NBC baseline; B = MOPP context variant. Do not include the full 9-line—let the workflow generate it.

**Pass/Fail:** ⬜

---

#### Should NOT TRIGGER CASEVAC:

**Test Messages (should not trigger):**

1. **General Medical Discussion:**
   - ❌ "How's the medic doing today?"
   - ❌ "Remember to take your malaria meds"
   - ❌ "Medical supplies arrived at checkpoint"

2. **Non-Urgent Injuries:**
   - ❌ "Twisted my ankle during PT, gonna rest"
   - ❌ "Got a headache, taking ibuprofen"
   - ❌ "Minor cut, bandaged it up"

3. **Historical/Training Context:**
   - ❌ "Last week's MEDEVAC drill went well"
   - ❌ "Reviewing 9-line procedures for training"
   - ❌ "Remember the CASEVAC brief from yesterday"

4. **Unrelated Urgent Situations:**
   - ❌ "Enemy contact! Return fire!"
   - ❌ "Vehicle broke down, need mechanic"
   - ❌ "Low on ammo, request resupply"

**Expected:**
- ✅ `AIService.detectIntent(chatId, 20)` returns `intent: "none"` or other intent
- ✅ Confidence < 0.5 for CASEVAC
- ✅ No false positives

**Pass/Fail:** ⬜

---

### Test 26: CASEVAC Workflow Persistence
**Objective:** Verify workflow survives app restart

**Steps:**
1. Trigger CASEVAC workflow
2. Immediately force-close app (don't wait for completion)
3. Reopen app
4. Wait 30-60 seconds

**Expected:**
- ✅ WorkManager resumes workflow after app restart
- ✅ Completion notification still shown
- ✅ Mission created in Firestore

**Pass/Fail:** ⬜

---

### Test 27: CASEVAC Retry on Failure
**Objective:** Verify exponential backoff on network failure

**Steps:**
1. Turn on airplane mode
2. Trigger CASEVAC workflow
3. Wait 30 seconds (first retry)
4. Turn off airplane mode
5. Wait for completion

**Expected:**
- ✅ Initial attempt fails (no network)
- ✅ WorkManager retries after 30s backoff
- ✅ Subsequent retry succeeds when network available

**Pass/Fail:** ⬜

---

### Test 28: Facility Lookup Accuracy
**Objective:** Verify nearest facility calculation

**Steps:**
1. Create facilities at known distances:
   - Facility A: 10 miles north
   - Facility B: 5 miles south
   - Facility C: 20 miles east (unavailable)
2. Mock location
3. Call `FacilityService.nearest(lat, lon)`

**Expected:**
- ✅ Returns Facility B (closest and available)
- ✅ Facility C excluded (unavailable=false)

**Pass/Fail:** ⬜

---

## Integration Testing

### Test 29: End-to-End AI Flow
**Objective:** Verify complete flow from Android to LangChain and back

**Steps:**
1. Send chat message: "Summarize recent patrol activity"
2. Trigger SITREP generation
3. Monitor full request chain

Seeded chat guidance
- Use Patrol Chat A for a clean SITREP summary. Alternate: Patrol Chat B to confirm SITREP includes MOPP-driven impacts (slower movement, lens fogging) without explicit contamination language.

**Expected Chain:**
```
Android App
  → Firebase Auth (ID token)
    → Firebase Functions (aiRouter)
      → HMAC signature
        → LangChain Service (/sitrep/summarize)
          → Firestore (fetch messages)
            → OpenAI API (chat completion)
              → RAG context building
                ← Response markdown
              ← Response to Functions
            ← Response to App
          ← Display in UI
```

**Verification:**
- ✅ Each step logs successfully
- ✅ Total latency < 10s
- ✅ No errors in any component
- ✅ Markdown displayed in app

**Pass/Fail:** ⬜

---

### Test 30: Offline-to-Online Transition
**Objective:** Verify queued operations execute when connectivity restored

**Steps:**
1. Turn on airplane mode
2. Trigger 3 AI operations:
   - Generate template
   - Extract threats
   - Generate SITREP
3. Verify jobs queued (check WorkManager)
4. Turn off airplane mode
5. Wait for execution

**Expected:**
- ✅ All 3 jobs queued while offline
- ✅ All 3 jobs execute when online
- ✅ Results appear in UI/Firestore
- ✅ No duplicate operations

**Pass/Fail:** ⬜

---

## Performance Testing

### Test 31: AI Call Latency
**Objective:** Measure AI endpoint response times

**Steps:**
1. Make 10 calls to each endpoint
2. Record latencies

**Expected:**
- Template generation: 2-5s (avg)
- Threat extraction: 2-5s (avg)
- SITREP summarization: 3-8s (avg)
- Intent detection: 1-3s (avg)

**Results:**
- Template: _____ ms (avg)
- Threats: _____ ms (avg)
- SITREP: _____ ms (avg)
- Intent: _____ ms (avg)

**Pass/Fail:** ⬜

---

### Test 32: Real-Time Update Latency
**Objective:** Measure Firestore listener latency

**Steps:**
1. Open MissionBoardScreen
2. Update mission from another source
3. Record time until UI updates

**Expected:**
- ✅ Update appears in < 2 seconds
- ✅ No UI jank or lag

**Pass/Fail:** ⬜

---

### Test 33: CASEVAC Workflow Duration
**Objective:** Measure end-to-end CASEVAC time

**Steps:**
1. Trigger CASEVAC with timer
2. Record completion notification time

**Expected:**
- ✅ Total duration < 60 seconds
- ✅ Typical: 5-15 seconds

**Duration:** _____ seconds

**Pass/Fail:** ⬜

---

## Security Testing

### Test 34: Unauthorized Access Prevention
**Objective:** Verify security controls prevent unauthorized access

**Steps:**
1. Attempt to call Firebase Function without auth token
2. Attempt to call with expired token
3. Attempt to access other user's data

**Expected:**
- ✅ All attempts return 401 Unauthorized
- ✅ No data exposed

**Pass/Fail:** ⬜

---

### Test 35: Rate Limiting Bypass Attempt
**Objective:** Verify rate limiting cannot be bypassed

**Steps:**
1. Make 25 rapid requests from same user
2. Try from different IP
3. Try with different request IDs

**Expected:**
- ✅ Requests 21-25 return 429 Rate limit exceeded
- ✅ Different IP doesn't bypass (same user)
- ✅ Different request IDs don't bypass

**Pass/Fail:** ⬜

---

## Error Handling Testing

### Test 36: Network Timeout Handling
**Objective:** Verify graceful handling of slow responses

**Steps:**
1. Simulate slow network (developer options)
2. Trigger AI operation
3. Observe UI and logs

**Expected:**
- ✅ Loading indicator shown
- ✅ Timeout after 30s (configurable)
- ✅ Error message shown to user
- ✅ No crash

**Pass/Fail:** ⬜

---

### Test 37: Malformed Response Handling
**Objective:** Verify app handles unexpected API responses

**Steps:**
1. Mock API to return invalid JSON
2. Mock API to return missing fields
3. Trigger operations

**Expected:**
- ✅ App doesn't crash
- ✅ Result.failure returned
- ✅ Error logged
- ✅ User-friendly error message

**Pass/Fail:** ⬜

---

## Regression Testing

### Test 38: Existing Features Unaffected
**Objective:** Verify AI integration doesn't break existing features

**Steps:**
1. Send text message (core chat)
2. Send image (media upload)
3. View chat history (paging)
4. View presence indicators
5. Create group chat

**Expected:**
- ✅ All existing features work as before
- ✅ No new crashes
- ✅ No performance degradation

**Pass/Fail:** ⬜

---

## Test Summary

**Total Tests:** 38  
**Passed:** _____  
**Failed:** _____  
**Blocked:** _____  

**Critical Failures:** (List any blocking issues)

**Minor Issues:** (List non-blocking issues)

**Recommendations:**

**Sign-off:**
- Tester: _______________
- Date: _______________
- Approved for Production: ☐ Yes ☐ No ☐ Conditional

---

## Notes

### Known Limitations (Acceptable for MVP)
- Some LangChain endpoints are stubs (return empty/placeholder data)
- In-memory rate limiting (resets on cold start)
- In-memory RAG cache (no persistence)
- No unit tests for some modules
- Limited error logging in some components

### Test Environment Details
- Android Version: _____
- Device Model: _____
- App Version: _____
- Firebase Project: _____
- LangChain Service URL: _____

### Additional Test Data
Attach logs, screenshots, or recordings as needed.

