# MessageAI Sprint 2 PRD – Tactical Infantry Officer AI Features (LangChain Integration)
**Stack:** Kotlin + Jetpack Compose + Firebase prototype + LangChain (Python) microservice  
**Sprint Length:** 7 days  
**Owner:** Android team (Kotlin)  
**Objective:** Implement persona-specific AI features and supporting modules on top of the MVP messaging infrastructure, backed by a LangChain service.

---

## 🧭 Sprint Overview
Sprint 2 extends the MVP (authentication, chat, offline queue, media, notifications) with **AI augmentation for tactical operations**.  
Focus is on **on-device situational awareness**, **rapid information extraction**, and **AI-assisted reporting**.

### Sprint Goals
1. Deliver five persona-aligned AI features + one advanced multi-step agent.  
2. Maintain sub-2 s latency for common AI requests; < 8 s for agent flows.  
3. Integrate AI module cleanly into existing Compose/Hilt architecture.  
4. Keep all APIs and AI calls **modular, provider-swappable, and secure** — Firebase and OpenAI act only as prototype providers.  
5. Integrate at least three LLM-backed functions (Template Auto-Fill, Alerts/Threat Summary, SITREP Generator) using a **LangChain (Python) service** with RAG and multi-step agent workflows, called via a Firebase Cloud Function proxy.

---

## 🧩 System Architecture Carry-Forward

| Layer | Purpose | Key Modules / Notes |
|-------|----------|--------------------|
| **UI (Compose)** | Screens + dialogs for chat, AI actions, mission dashboard | Reuse MVP navigation; extend with `AIActionSheet`, `MissionView`, `TemplateWizard` |
| **Domain Logic** | Business use cases for messaging and AI | Add `AIFeatureInteractor.kt` to coordinate message → AI → response flow |
| **Data Layer** | Firestore, Room, Storage | Extend message schema with `metadata.aiContext` and `geoTag` fields |
| **Background Sync** | WorkManager queues | Extend existing sync to include AI tasks and report uploads |
| **AI Module (new)** | Provider-agnostic interface for LLM operations | Resides under `/modules/ai/`; uses Hilt to inject chosen provider |
| **Geo Module (new)** | Location extraction + alert logic | Wraps FusedLocationProviderClient; pluggable with mil-grade GPS service |
| **Reporting Module (new)** | SITREP + template generation | Generates PDF/Markdown reports; future hook for classified exports |

**Extensibility / Deployment Path Note:**  
Every provider implements a lightweight interface (`IAIProvider`, `IGeoProvider`, `IReportProvider`).  
Client calls route through a **Firebase Cloud Function proxy** to a **LangChain (Python) microservice** that performs RAG and agent workflows.  
For DoD/on-prem, swap the Cloud Function base URL and the LangChain service host (same REST contract). No UI or business logic rewrites required.

---

## ⚙️ Architecture Additions

### LangChain Service (Python) – Primary AI Engine
Endpoints (initial):
```
POST /template/generate         // NATO autofill (RAG-enhanced)
POST /threats/extract           // Threat summarization + scoring
POST /sitrep/summarize          // SITREP generator (RAG)
POST /intent/casevac/detect     // Semantic CASEVAC intent detection
POST /workflow/casevac/run      // Multi-step CASEVAC agent
```
Infra:
- Containerized (Docker), 1–2 replicas, persistent logs.
- Retrieval layer: Firestore reader + lightweight embeddings store.
- Observability: requestId propagation, structured JSON logs, latency histograms.

---

## 🚀 Feature Specifications

### **1. NATO Template Auto-Fill**
**User Story**  
“As a platoon leader, I want to generate standard NATO reports (MEDEVAC, WARNORD, OPORD) from plain language so I can communicate faster under pressure.”

**Requirements**
- Parse free-text commands → populate 9-Line MEDEVAC (and other NATO templates).  
- Validate mandatory fields; flag missing data.  
- Export as formatted message or PDF.  

**Technical Implementation**
- `AIService.generateTemplate(prompt, type)` → Firebase CF proxy → **LangChain `/template/generate`**.  
- Compose form shows AI-suggested autofill fields with confidence scores.  
- The function includes **RAG retrieval** from recent chat history (Room) for contextual accuracy.  
- Results cached locally for offline review and approval.  

**Acceptance Criteria**
- [ ] Generation < 3 s for standard templates  
- [ ] Editable fields auto-filled > 80 % accuracy with confidence values  
- [ ] No network = graceful degradation (local cached forms)

---

### **2. Geolocation Intelligence + Signal Loss Alerts**
**User Story**  
“As a leader, I need to auto-extract coordinates and receive alerts when a squad member’s signal drops so I can respond immediately.”

**Requirements**
- NLP extraction of grid refs / lat-long from messages.  
- Track device pings and alert on loss > 30 s.  
- Display last known position on map.  

**Technical Implementation**
- `GeoService.monitorPresence()` listens to Firestore presence docs.  
- `AIService.extractLocation(text)` for coordinate parsing.  
- `NotificationManager` + map intent for alerts.  

**Acceptance Criteria**
- [ ] Coordinate extraction accuracy ≥ 90 % for MGRS or lat/long  
- [ ] Signal loss alert within 10 s of disconnect  
- [ ] Location display refresh ≤ 1 Hz  

**Extensibility Note:** Firebase presence → plug in MILNET socket feed without schema change.

---

### **3. Threat Summary + Geo-Fenced Alerts**
**User Story**  
“As a leader, I want threat intel from messages summarized and warn me when my unit approaches danger zones.”

**Requirements**
- Summarize messages flagged as threat intel.  
- Maintain geofenced areas (threat radius, timestamp).  
- Auto-notify when entering radius.  

**Technical Implementation**
- Firestore collection `threats` with geoHash index.  
- `AIService.extractThreats(chatId)` → Firebase CF proxy → **LangChain `/threats/extract`** agent that classifies message-level threats and assigns severity/confidence.  
- Results stored with geolocation metadata for alerting.  
- `GeoService.monitorGeofence()` runs WorkManager task to trigger local notifications.  

**Acceptance Criteria**
- [ ] Accurate threat clustering and AI-generated summaries  
- [ ] Alerts fire consistently with < 5 m radius error  

---

### **4. Mission Status Tracker**
**User Story**  
“I need a dashboard of tasks, objectives, and personnel statuses so I can maintain situational awareness.”

**Requirements**
- Extract tasks/objectives from chats.  
- Track completion and timestamp updates.  
- Display summary cards (per user / unit).  

**Technical Implementation**
- `AIService.extractTasks()` → Firestore `missions` collection.  
- Compose `MissionBoard` UI with Flow subscription.  

**Acceptance Criteria**
- [ ] Dashboard updates in < 2 s after message arrival  
- [ ] Tasks parsed with > 85 % precision  

**Extensibility Note:** AI module independent of UI; backend can swap to classified mission database via new provider.

---

### **5. SITREP Auto-Generator**
**User Story**  
“As a leader, I need to compile activity into a SITREP for higher HQ without manual editing.”

**Requirements**
- Aggregate last 6/12/24 hours of messages.  
- Summarize events by time, location, outcome.  
- Export as PDF/Markdown and share via chat.  

**Technical Implementation**
- `ReportService.generateSITREP(timeWindow)` → calls `AIService.summarize()` → triggers **LangChain RAG pipeline** via `/sitrep/summarize`.  
- PDF render with ReportLab (Compose integration).  
- Output saved in local cache for offline access and shareable via chat.  

**Acceptance Criteria**
- [ ] Report generation < 10 s including AI summarization  
- [ ] Readable summary accuracy ≥ 90 % vs manual report  
- [ ] At least one LangChain workflow successfully invoked (logged `requestId`)  

---

### **Advanced Feature – Multi-Step Agent: CASEVAC Coordinator**
**User Story**  
“When a casualty is reported, I need an AI agent that auto-generates a 9-line MEDEVAC, identifies nearest medical asset, and tracks status until completion.”

**Workflow**
1. AI-driven event detection: `AIService.detectCasevacIntent(messages)` via **LangChain `/intent/casevac/detect`** (semantic classification; no regex).  
2. Auto-populate 9-line MEDEVAC (Feature 1).  
3. Query nearest medical facility (Geo module).  
4. Create mission entry (Feature 4).  
5. Update status until marked complete.  

**Technical Implementation**
- `AIService.runWorkflow("casevac")` calls **LangChain `/workflow/casevac/run`** (tool-calling orchestration).  
- Steps serialized as JSON plan; agent executes autonomously.  
- Background execution via WorkManager.  

**Acceptance Criteria**
- [ ] End-to-end workflow < 60 s  
- [ ] ≥ 90 % correct data population  
- [ ] Updates propagate to Mission Tracker  
- [ ] No deterministic regex or keyword parsing; AI inference only.  

**Extensibility Note:** Cloud Functions plan executor can be replaced with on-prem Agent Orchestrator (API-compatible).

---

## 🧠 AI Module Technical Design

| Component | Responsibility |
|------------|----------------|
| `AIService` | Central facade for all AI calls; handles context assembly and provider routing |
| `IAIProvider` | Interface for model calls (`generate()`, `extract()`, `analyze()`, `detectIntent()`) |
| `LangChainAdapter` | HTTP client for LangChain endpoints via Firebase CF proxy |
| `OpenAIProvider` | (Optional) Direct completion proxy for simple prompts |
| `LocalProvider` | Offline/mock responses for testing |
| `AICache` | Stores recent prompts/responses locally |
| `AIMemory` | Conversation RAG context from Room DB messages |
| `SecurityLayer` | Token signing + Keystore encryption for API auth |

**Interface Example**
```kotlin
interface IAIProvider {
    suspend fun generateTemplate(type: TemplateType, context: String): Result<Template>
    suspend fun extractGeoData(text: String): Result<GeoPoint?>
    suspend fun summarizeThreats(messages: List<Message>): Result<List<ThreatAlert>>
    suspend fun detectIntent(messages: List<Message>): Result<DetectedIntent?>
    suspend fun runLangChainWorkflow(endpoint: String, payload: Map<String, Any>): Result<WorkflowResponse>
}
```
**Workflow Integration Note:**  
AIService includes a **LangChainAdapter** that targets the endpoints above via the Firebase CF proxy.  
Each call carries `requestId`, `contextWindow`, `sig`, and minimal PII.  
Switching to DoD infra = swap the base URL (same JSON contract).

---

## 🧾 Sprint Deliverables
1. `ai`, `geo`, and `reporting` modules implemented and Hilt-bound.  
2. All five AI features + CASEVAC agent functional in UI.  
3. Demo APK capable of offline use and AI replay tests.  
4. Technical README explaining provider swap architecture **plus LangChain service endpoints and JSON contracts**.  
5. **Dockerfile + Runbook** for LangChain service (build, run, env vars, warmup).  
6. **Postman collection** for `/template`, `/threats`, `/sitrep`, `/intent/casevac`, `/workflow/casevac`.  

---

## 🧪 Testing & Validation
- **Latency Test:** Record response times for each AI call (P95).  
- **Warmth Test:** Validate latency warm vs cold containers; document strategy.  
- **Endpoint Contract Test:** CI verifies CF proxy ↔ LangChain JSON schemas.  
- **Offline Test:** Simulate no network → verify queued AI requests process on reconnect.  
- **Integration Test:** Ensure Mission Tracker updates when CASEVAC agent runs.  

---

## 📈 Success Metrics (Aligned to Rubric)
| Section | Target |
|----------|---------|
| **Required AI Features** | All 5 implemented via LangChain RAG + agent flows |
| **Persona Fit** | All features support situational awareness and automation |
| **Advanced Capability** | CASEVAC agent executes ≥ 5 steps autonomously in ≤ 60 s |
| **Architecture** | Modular provider interfaces; secure CF proxy to LangChain |
| **LLM Workflows** | ≥3 LangChain RAG/agent workflows working end-to-end |
| **Documentation** | README + API contracts + Docker runbook |

---

## ⚠️ Risks & Mitigations
| Risk | Mitigation |
|------|-------------|
| LLM latency > target | Cache outputs locally; warm LangChain containers; prefetch embeddings |
| Cold starts (Python) | Min replicas ≥1; periodic warmers & health checks |
| API drift | Schema tests between CF and LangChain |
| Key exposure | Keys stored server-side only |
| Vendor lock-in | Same REST schema across providers |

---

## 🏁 End of Sprint Definition of Done
- [ ] All features demonstrated live on emulator/physical device.  
- [ ] Code passes lint, tests, and compiles with stable Gradle build.  
- [ ] AI modules fully Hilt-injected and swap-ready.  
- [ ] README + architecture diagram + API contracts added to repo.  
- [ ] APK submitted for rubric evaluation.
