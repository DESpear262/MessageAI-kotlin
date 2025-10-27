# TICKET: Add README Documentation for Geo and Reporting Modules

**Status:** 🟡 Backlog  
**Priority:** Low  
**Type:** Documentation  
**Estimated Effort:** 2 hours  
**Assignee:** TBD  
**Created:** 2025-10-24

---

## Problem Summary

Blocks C (Geo Intelligence) and D (Reporting) lack module-level documentation:

**Current State:**
- ❌ No `README.md` in `/modules/geo/`
- ❌ No `README.md` in `/modules/reporting/`
- ⚠️ Inline KDoc exists but no high-level overview
- ⚠️ No usage examples for new developers

**Impact:**
- Medium: New developers struggle to understand modules
- Onboarding: Takes longer to understand architecture
- Maintainability: Hard to know what each module does

**Comparison:**
- ✅ Block A has excellent README (`/modules/ai/README.md`, 365 lines)
- ❌ Blocks C & D have none

---

## Solution

Create comprehensive README files for both modules following Block A's pattern.

### README Template Structure

1. **Overview** - Purpose and responsibilities
2. **Architecture** - Component diagram
3. **Components** - Key classes and their roles
4. **Usage Examples** - Code samples
5. **Integration Points** - Dependencies and interactions
6. **Configuration** - Constants and settings
7. **Testing** - How to test the module
8. **Future Improvements** - Known limitations

---

## Implementation

### Block C: Geo Intelligence README

**File:** `app/src/main/java/com/messageai/tactical/modules/geo/README.md`

```markdown
# Geo Intelligence Module - Block C

## Overview

The Geo Intelligence Module provides location-based threat awareness and alerting capabilities for tactical operations. It integrates with the AI Core Module (Block A) to extract, analyze, and monitor threats using LangChain-powered intelligence.

## Architecture

```
┌─────────────────┐
│   GeoService    │ ← Threat management & alerts
└────────┬────────┘
         │
    ┌────┴────┬──────────────┬─────────────┐
    │         │              │             │
┌───▼────┐ ┌─▼─────────┐ ┌──▼────────┐ ┌─▼──────────┐
│AIService│ │ Firestore │ │Fused Loc  │ │Notification│
│(Block A)│ │ (threats) │ │ Provider  │ │  Manager   │
└─────────┘ └───────────┘ └───────────┘ └────────────┘
```

## Components

### GeoService

Central service for geolocation intelligence operations.

**Key Responsibilities:**
- AI-powered threat extraction from chat messages
- Geofence monitoring and proximity alerts
- Signal loss detection and notifications
- Threat persistence with automatic expiry (8 hours)

**Constructor:**
```kotlin
class GeoService(
    private val context: Context,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val aiService: AIService  // Block A integration
)
```

### Key Methods

#### analyzeChatThreats()

Analyzes recent chat messages using AI to extract and persist threats.

```kotlin
@SuppressLint("MissingPermission")
fun analyzeChatThreats(
    chatId: String,
    maxMessages: Int = 100,
    onComplete: ((Int) -> Unit)? = null
)
```

**Flow:**
1. Fetch device location (fallback for threats without coordinates)
2. Call `AIService.summarizeThreats()` → LangChain `/threats/extract`
3. Parse AI response for threat data
4. Persist threats to Firestore `/threats` collection
5. Invoke completion callback with count

**Usage:**
```kotlin
@Inject lateinit var geoService: GeoService

fun extractThreats() {
    geoService.analyzeChatThreats(
        chatId = currentChatId,
        maxMessages = 100,
        onComplete = { count ->
            showToast("Extracted $count threats")
        }
    )
}
```

#### summarizeThreatsNear()

Retrieves threats within specified radius, filtered by recency and severity.

```kotlin
fun summarizeThreatsNear(
    latitude: Double,
    longitude: Double,
    maxMiles: Double = 500.0,
    limit: Int = 50,
    onAlert: (Threat) -> Unit
)
```

**Filtering:**
- ✅ Recency: Only threats < 8 hours old
- ✅ Proximity: Within specified radius (miles)
- ✅ Ranking: Sorted by severity (desc) then timestamp (desc)

**Usage:**
```kotlin
geoService.summarizeThreatsNear(
    latitude = 34.0522,
    longitude = -118.2437,
    maxMiles = 50.0,
    limit = 10
) { threat ->
    Log.d(TAG, "Threat: ${threat.summary} at (${threat.lat}, ${threat.lon})")
}
```

#### checkGeofenceEnter()

Checks if current location has entered any threat geofences.

```kotlin
fun checkGeofenceEnter(
    latitude: Double,
    longitude: Double,
    onEnter: (Threat) -> Unit
)
```

**Behavior:**
- Queries all active threats (< 8 hours old)
- Calculates distance to each threat center
- Triggers alert if within threat radius
- Shows notification automatically

**Usage:**
```kotlin
// In location update listener
locationProvider.updates.collect { location ->
    geoService.checkGeofenceEnter(
        latitude = location.latitude,
        longitude = location.longitude
    ) { threat ->
        // Threat entered, show detailed alert
        showThreatDetails(threat)
    }
}
```

#### alertSignalLossIfNeeded()

Fires alert after consecutive heartbeat misses.

```kotlin
fun alertSignalLossIfNeeded(consecutiveMisses: Int)
```

**Configuration:**
- Threshold: 2 consecutive misses
- Alert: High-priority notification

**Usage:**
```kotlin
// In NetworkHeartbeat or similar
if (heartbeatFailed) {
    missCount++
    geoService.alertSignalLossIfNeeded(missCount)
} else {
    missCount = 0
}
```

### Data Models

#### Threat

Represents a tactical threat with geolocation.

```kotlin
data class Threat(
    val id: String,
    val summary: String,
    val severity: Int,        // 1-5 (1=lowest, 5=critical)
    val lat: Double,
    val lon: Double,
    val radiusM: Int,         // Threat radius in meters
    val ts: Long              // Timestamp (ms)
)
```

### Constants

```kotlin
companion object {
    private const val THREATS_COLLECTION = "threats"
    private const val DEFAULT_RADIUS_M = 500  // 500 meters
    private const val EIGHT_HOURS_MS = 8 * 60 * 60 * 1000L
}
```

## Integration Points

### Dependencies

- **AIService (Block A):** Threat extraction and geo parsing
- **Firestore:** Threat persistence (`/threats` collection)
- **FusedLocationProviderClient:** Device location
- **NotificationManager:** Alerts and warnings
- **Firebase Auth:** User context

### Firestore Schema

```
/threats/{threatId}
  - summary: string          // Threat description
  - severity: number         // 1-5
  - confidence: number       // 0.0-1.0
  - geo: {
      lat: number,
      lon: number
    }
  - radiusM: number          // Threat radius in meters
  - ts: Timestamp            // Creation time
```

**Indexing:**
- Index on `ts` (descending) for efficient recent queries

**Cleanup:**
- TTL: 8 hours (hardcoded)
- Consider Firestore TTL policy for automatic cleanup

### Hilt DI

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object GeoModule {
    @Provides
    @Singleton
    fun provideGeoService(
        @ApplicationContext context: Context,
        firestore: FirebaseFirestore,
        auth: FirebaseAuth,
        aiService: AIService  // Injected from AIModule
    ): GeoService = GeoService(context, firestore, auth, aiService)
}
```

## Configuration

### Permissions Required

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

**Runtime Checks:**
- Location: `@SuppressLint("MissingPermission")` assumes granted (TODO: add checks)
- Notifications: Android 13+ check in `showAlert()`

### Notification Channel

```kotlin
Channel ID: "alerts_channel"
Name: "Operational Alerts"
Importance: HIGH
Icon: android.R.drawable.stat_notify_error
```

## Testing

### Unit Tests (TODO)

```kotlin
class GeoServiceTest {
    @Test
    fun metersBetween_calculateDistance() {
        val meters = GeoService.metersBetween(0.0, 0.0, 0.01, 0.01)
        // Assert approximate distance
    }
    
    @Test
    fun analyzeChatThreats_callsAIService() = runTest {
        // Mock AIService
        // Verify summarizeThreats called
    }
}
```

### Integration Tests

1. **Threat Extraction Flow:**
   ```bash
   # 1. Send test messages with threat keywords
   # 2. Call analyzeChatThreats()
   # 3. Verify threats in Firestore
   # 4. Check AI service was called
   ```

2. **Geofence Alerts:**
   ```bash
   # 1. Add test threat near location
   # 2. Update location to within radius
   # 3. Verify notification shown
   ```

## Known Limitations

1. **Mixed Async Patterns:** Uses both Tasks API and Coroutines (see ticket BLOCK-C-001)
2. **No Error Propagation:** Silent failures in `analyzeChatThreats()` (see ticket BLOCK-C-002)
3. **Permission Assumptions:** `@SuppressLint("MissingPermission")` instead of runtime checks
4. **No Threat Deduplication:** May create duplicate Firestore documents
5. **Hardcoded Constants:** 8-hour expiry, 500m radius not configurable
6. **In-Memory State:** Signal loss tracking resets on app restart

## Future Improvements

- [ ] Standardize on coroutines (remove Tasks API)
- [ ] Add proper error callbacks
- [ ] Implement threat deduplication
- [ ] Make constants configurable
- [ ] Add unit test coverage
- [ ] Remove `@SuppressLint`, add runtime permission checks
- [ ] Add WorkManager for periodic geofence checks
- [ ] Implement threat clustering (nearby threats grouped)
- [ ] Add threat priority levels
- [ ] Support custom geofence shapes (not just circles)

## Related Modules

- **Block A (AI Core):** Provides threat extraction via AIService
- **Block D (Reporting):** May use threat data for SITREP generation
- **NetworkHeartbeat:** Calls `alertSignalLossIfNeeded()`
- **GeofenceWorker:** Periodic geofence monitoring

## QC Report

See `docs/reviews/BLOCKS_C_D_QC_REPORT.md` for comprehensive review.

---

**Module Lead:** TBD  
**Sprint:** Sprint 2 - AI Integration  
**Status:** ✅ Complete (MVP)
```

---

### Block D: Reporting Module README

**File:** `app/src/main/java/com/messageai/tactical/modules/reporting/README.md`

```markdown
# Reporting Module - Block D

## Overview

The Reporting Module provides AI-powered generation of tactical reports including SITREP summaries and NATO standard templates (WARNORD, OPORD, FRAGO). It integrates with the LangChain service (Block B2) to generate markdown reports from chat history.

## Architecture

```
┌──────────────────┐
│  ReportService   │ ← Report generation
└────────┬─────────┘
         │
    ┌────┴────┐
    │         │
┌───▼──────┐ ┌────▼──────────┐
│LangChain │ │ ReportViewModel│
│ Adapter  │ │    (MVVM)      │
│(Block B) │ └────┬───────────┘
└──────────┘      │
              ┌───▼──────────┐
              │ReportPreview │
              │   Screen     │
              └───┬──────────┘
                  │
              ┌───▼──────────┐
              │ ReportShare  │
              │(FileProvider)│
              └──────────────┘
```

## Components

### ReportService

Core service for report generation via LangChain.

**Constructor:**
```kotlin
class ReportService(
    private val adapter: LangChainAdapter
)
```

#### generateSITREP()

Generates situation report summarizing recent chat activity.

```kotlin
suspend fun generateSITREP(
    chatId: String,
    timeWindow: String = "6h"
): Result<String>
```

**LangChain Integration:**
- Endpoint: `POST /sitrep/summarize`
- RAG: Fetches recent messages from Firestore
- LLM: OpenAI generates markdown summary

**Usage:**
```kotlin
@Inject lateinit var reportService: ReportService

suspend fun loadSitrep() {
    reportService.generateSITREP(chatId = "chat123", timeWindow = "6h")
        .onSuccess { markdown ->
            displayReport(markdown)
        }
        .onFailure { error ->
            showError("SITREP generation failed: ${error.message}")
        }
}
```

#### generateWarnord() / generateOpord() / generateFrago()

Generate NATO standard templates.

```kotlin
suspend fun generateWarnord(): Result<String>
suspend fun generateOpord(): Result<String>
suspend fun generateFrago(): Result<String>
```

**LangChain Integration:**
- Endpoints: `POST /template/warnord`, `/template/opord`, `/template/frago`
- Returns markdown templates from repository files

**Usage:**
```kotlin
val result = reportService.generateWarnord()
result.onSuccess { markdown -> displayTemplate(markdown) }
```

### ReportViewModel

MVVM ViewModel for report UI state management.

```kotlin
@HiltViewModel
class ReportViewModel @Inject constructor(
    private val reportService: ReportService
) : ViewModel() {
    val markdown: StateFlow<String?>
    val loading: StateFlow<Boolean>
    
    fun loadSitrep(chatId: String, window: String = "6h")
    fun loadTemplate(kind: String)
}
```

**State Management:**
- `markdown`: Current report content (or null)
- `loading`: Loading state for UI progress indicator

**Usage:**
```kotlin
@HiltViewModel
val viewModel: ReportViewModel = hiltViewModel()

val markdown by viewModel.markdown.collectAsState()
val loading by viewModel.loading.collectAsState()

LaunchedEffect(Unit) {
    viewModel.loadSitrep(chatId, "6h")
}
```

### ReportPreviewScreen

Compose UI for displaying and sharing reports.

```kotlin
@Composable
fun ReportPreviewScreen(
    chatId: String?,
    kind: String,  // "sitrep" | "warnord" | "opord" | "frago"
    onShare: (String) -> Unit,
    viewModel: ReportViewModel = hiltViewModel()
)
```

**Features:**
- Loading indicator during generation
- Markdown preview (monospace font)
- Floating action button for sharing
- Auto-loads on composition

**Usage:**
```kotlin
NavHost {
    composable("report/{kind}") { backStackEntry ->
        val kind = backStackEntry.arguments?.getString("kind") ?: "sitrep"
        ReportPreviewScreen(
            chatId = currentChatId,
            kind = kind,
            onShare = { markdown ->
                ReportShare.shareMarkdown(context, "report.md", markdown)
            }
        )
    }
}
```

### ReportShare

Utility for exporting reports via Android share sheet.

```kotlin
object ReportShare {
    fun shareMarkdown(
        context: Context,
        fileName: String,
        content: String
    )
}
```

**Implementation:**
- Writes markdown to app cache directory
- Uses FileProvider for secure file sharing
- Opens Android share sheet (email, messaging, etc.)

**Usage:**
```kotlin
ReportShare.shareMarkdown(
    context = context,
    fileName = "sitrep_${timestamp}.md",
    content = markdownContent
)
```

## Integration Points

### Dependencies

- **LangChainAdapter (Block B):** Forwards requests to LangChain service
- **LangChain Service (Block B2):** AI-powered report generation
- **Firestore:** Source of chat messages for RAG
- **FileProvider:** Secure file sharing

### LangChain Endpoints

**SITREP:**
```
POST /sitrep/summarize
Body: {
  "requestId": "uuid",
  "context": { "chatId": "..." },
  "payload": { "timeWindow": "6h" }
}
Response: {
  "requestId": "uuid",
  "status": "ok",
  "data": {
    "format": "markdown",
    "content": "# SITREP\n\n..."
  }
}
```

**Templates:**
```
POST /template/{warnord|opord|frago}
Body: { "requestId": "uuid", "context": {}, "payload": {} }
Response: {
  "requestId": "uuid",
  "status": "ok",
  "data": {
    "templateType": "WARNORD",
    "content": "# WARNING ORDER\n\n..."
  }
}
```

### Hilt DI

```kotlin
// In AIModule
@Provides
@Singleton
fun provideReportService(
    adapter: LangChainAdapter
): ReportService = ReportService(adapter)
```

**Why AIModule?**
- ReportService uses LangChainAdapter (provided by AIModule)
- Keeps AI-related services together
- Could be moved to separate ReportingModule if desired

## Configuration

### FileProvider Setup

```xml
<!-- AndroidManifest.xml -->
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

```xml
<!-- res/xml/file_paths.xml -->
<paths>
    <cache-path name="reports" path="reports/" />
</paths>
```

### LangChain Service Configuration

```bash
# Environment variables for LangChain service
OPENAI_API_KEY=sk-...
FIRESTORE_PROJECT_ID=messageai-kotlin
```

## Testing

### Unit Tests (TODO)

```kotlin
class ReportServiceTest {
    @Test
    fun generateSITREP_success_returnsMarkdown() = runTest {
        val mockAdapter = mockk<LangChainAdapter>()
        coEvery { mockAdapter.post(any(), any(), any()) } returns mockResponse
        
        val service = ReportService(mockAdapter)
        val result = service.generateSITREP("chat123", "6h")
        
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()?.contains("# SITREP") == true)
    }
}
```

### Integration Tests

1. **SITREP Generation:**
   ```bash
   # 1. Populate test chat with messages
   # 2. Call generateSITREP()
   # 3. Verify markdown contains expected sections
   # 4. Verify LangChain service called
   ```

2. **Export Flow:**
   ```bash
   # 1. Generate report
   # 2. Click share button
   # 3. Verify share sheet appears
   # 4. Verify file accessible by sharing apps
   ```

## Known Limitations

1. **No PDF Generation:** Markdown only (task plan mentioned PDF)
2. **No Report Caching:** Regenerates on every screen visit
3. **Technical Error Messages:** Not user-friendly
4. **Template Endpoints May Be Stubs:** LangChain service returns static markdown

## Future Improvements

- [ ] Add PDF generation (coil + reportlab)
- [ ] Implement report caching (in-memory or Room)
- [ ] User-friendly error messages
- [ ] Template customization (user-provided values)
- [ ] Offline report generation (cached data)
- [ ] Report history (save generated reports)
- [ ] Multiple export formats (PDF, DOCX, HTML)
- [ ] Report scheduling (auto-generate at intervals)

## Related Modules

- **Block A (AI Core):** Could use AIService for template generation
- **Block B (Firebase Functions):** Proxy for LangChain calls
- **Block B2 (LangChain Service):** Backend report generation
- **Block C (Geo):** Could integrate threat data into SITREP

## QC Report

See `docs/reviews/BLOCKS_C_D_QC_REPORT.md` for comprehensive review.

---

**Module Lead:** TBD  
**Sprint:** Sprint 2 - AI Integration  
**Status:** ✅ Complete (MVP)
```

---

## Acceptance Criteria

- [ ] README.md created for `/modules/geo/`
- [ ] README.md created for `/modules/reporting/`
- [ ] Both READMEs follow Block A template structure
- [ ] Architecture diagrams included
- [ ] Usage examples provided
- [ ] Integration points documented
- [ ] Known limitations listed
- [ ] Future improvements outlined
- [ ] Code review approved

---

## Success Metrics

✅ **Definition of Done:**
1. Two README files created and committed
2. New developers can understand modules in < 15 minutes
3. All public APIs documented with examples
4. Integration points clearly explained
5. Peer review confirms completeness

---

**Created by:** QC Agent (Blocks C & D Review)  
**Related Sprint:** Sprint 2 - AI Integration (Documentation)  
**Blocks:** None (documentation)  
**Ticket ID:** BLOCK-C-D-001

