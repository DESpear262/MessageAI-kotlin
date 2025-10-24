# TICKET: Add Location Permission Checks to CasevacWorker

**Status:** 🟡 Backlog  
**Priority:** High  
**Type:** Bug / Security  
**Estimated Effort:** 1-2 hours  
**Assignee:** TBD  
**Created:** 2025-10-24

---

## Problem Summary

`CasevacWorker` uses device location without checking permissions:

```kotlin
// CasevacWorker.kt line 40
val loc = fused.lastLocation.awaitNullable()
val lat = loc?.latitude ?: 0.0
val lon = loc?.longitude ?: 0.0
```

**Problems:**
1. No permission check before accessing location
2. No `@SuppressLint("MissingPermission")` annotation
3. May crash on devices without granted permissions
4. Security best practice violation

**Impact:**
- High: Potential runtime crash
- Security: Permission violation
- UX: Silent failure (falls back to 0.0, 0.0)

---

## Root Cause

MVP implementation prioritized functionality over permission handling.

**Permission Required:**
```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

**Android Requirement:**
- API 23+ requires runtime permission checks
- Accessing location without granted permission throws SecurityException
- WorkManager runs in background, permission must be checked

---

## Solution

Add runtime permission check before accessing location.

### Option 1: Check Permission in Worker (Recommended)

**What:** Check permission in `doWork()` before accessing location

**Pros:**
- Defensive programming
- Worker won't crash
- Clear error handling

**Cons:**
- Permission should ideally be granted before worker enqueues

**Implementation:**

```kotlin
@HiltWorker
class CasevacWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val ai: AIService,
    private val missions: MissionService,
    private val facilities: FacilityService
) : CoroutineWorker(appContext, params) {

    private val fused: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(applicationContext)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val chatId = inputData.getString(KEY_CHAT_ID) ?: return@withContext Result.failure()
        val messageId = inputData.getString(KEY_MESSAGE_ID)
        
        try {
            CasevacNotifier.notifyStart(applicationContext, chatId)
            
            // 1) Generate 9-line (template)
            ai.generateTemplate(chatId, type = "MEDEVAC", maxMessages = 50)

            // 2) Determine nearest facility
            val loc = getLocationSafely()  // ← NEW: Safe location fetching
            val lat = loc?.latitude ?: 0.0
            val lon = loc?.longitude ?: 0.0
            val facility = facilities.nearest(lat, lon)

            // 3-4) Mission creation and status updates (unchanged)
            val createdId = missions.createMission(
                Mission(
                    chatId = chatId,
                    title = "CASEVAC",
                    description = facility?.name ?: "Nearest facility located",
                    status = "in_progress",
                    priority = 5,
                    assignees = emptyList(),
                    sourceMsgId = messageId,
                    casevacCasualties = 0
                )
            )
            missions.incrementCasevacCasualties(chatId, delta = 1)
            missions.updateMission(createdId, mapOf("status" to "done"))
            missions.archiveIfCompleted(createdId)

            CasevacNotifier.notifyComplete(applicationContext, facility?.name)
            Result.success()
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission denied", e)
            // Continue without location (use fallback 0.0, 0.0)
            Result.success()  // Don't retry for permission issues
        } catch (e: Exception) {
            Log.e(TAG, "CASEVAC workflow failed", e)
            Result.retry()
        }
    }

    /**
     * Safely fetches location with permission check
     * Returns null if permission not granted
     */
    private suspend fun getLocationSafely(): android.location.Location? {
        // Check permission
        if (!hasLocationPermission()) {
            Log.w(TAG, "Location permission not granted, using fallback coordinates")
            return null
        }
        
        // Permission granted, safe to access
        return try {
            fused.lastLocation.awaitNullable()
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException despite permission check", e)
            null
        }
    }

    /**
     * Checks if location permission is granted
     */
    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            applicationContext,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val TAG = "CasevacWorker"
        private const val KEY_CHAT_ID = "chatId"
        private const val KEY_MESSAGE_ID = "messageId"

        fun enqueue(context: Context, chatId: String, messageId: String?) {
            // Optional: Check permission before enqueueing
            if (!hasLocationPermissionStatic(context)) {
                Log.w(TAG, "Location permission not granted, CASEVAC will use fallback coordinates")
            }
            
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val input = workDataOf(KEY_CHAT_ID to chatId, KEY_MESSAGE_ID to messageId)
            val req = OneTimeWorkRequestBuilder<CasevacWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(input)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("casevac_$chatId", ExistingWorkPolicy.APPEND_OR_REPLACE, req)
        }
        
        private fun hasLocationPermissionStatic(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}

// Keep existing extension function
private suspend fun FusedLocationProviderClient.awaitNullable(): android.location.Location? =
    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        lastLocation.addOnSuccessListener { loc -> cont.resume(loc) {} }
            .addOnFailureListener { cont.resume(null) {} }
    }
```

**Required Import:**
```kotlin
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.util.Log
```

---

### Option 2: Assume Permission Granted (Quick Fix)

**What:** Add `@SuppressLint("MissingPermission")` annotation

**Pros:**
- Quick fix
- No code changes

**Cons:**
- Still crashes if permission not granted
- Security warning suppressed
- Not production-ready

**Implementation:**
```kotlin
@SuppressLint("MissingPermission")
override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
    // ... existing code
}
```

**Not recommended** - Doesn't solve underlying problem

---

## Recommended Solution: Option 1 (Permission Check)

### Key Changes
1. Add `hasLocationPermission()` helper method
2. Add `getLocationSafely()` wrapper for location access
3. Catch `SecurityException` explicitly
4. Log warnings when permission denied
5. Continue workflow with fallback (0.0, 0.0) if permission denied
6. Optional: Check permission in `enqueue()` companion

### Fallback Behavior
When permission denied:
- Use coordinates (0.0, 0.0) for facility lookup
- Facility lookup will find nearest facility globally
- Workflow continues successfully
- User still gets CASEVAC mission created

**Alternative:** Fail workflow if permission denied
```kotlin
if (!hasLocationPermission()) {
    Log.e(TAG, "Location permission required for CASEVAC")
    CasevacNotifier.notifyError(applicationContext, "Location permission required")
    return@withContext Result.failure()
}
```

---

## Acceptance Criteria

- [ ] Location permission checked before access
- [ ] No crashes when permission denied
- [ ] Graceful fallback behavior defined
- [ ] SecurityException caught and handled
- [ ] Warning logged when permission denied
- [ ] Optional notification to user about permission
- [ ] Unit tests cover permission denied case
- [ ] Manual testing confirms no crash
- [ ] Code review approved

---

## Testing Checklist

### Unit Tests
```kotlin
@Test
fun `doWork succeeds without location permission`() = runTest {
    // Mock permission denied
    // Mock workflow steps
    
    val result = worker.doWork()
    
    // Should still succeed with fallback
    assertEquals(ListenableWorker.Result.success(), result)
    
    // Verify facility lookup called with (0.0, 0.0)
    coVerify { facilityService.nearest(0.0, 0.0) }
}
```

### Manual Tests
```bash
# Test 1: Permission granted
# 1. Grant location permission
# 2. Trigger CASEVAC
# 3. Verify workflow uses actual location

# Test 2: Permission denied
# 1. Revoke location permission
# 2. Trigger CASEVAC
# 3. Verify no crash
# 4. Verify workflow completes with fallback
# 5. Check logcat for permission warning

# Test 3: Permission revoked during execution
# 1. Grant permission
# 2. Trigger CASEVAC
# 3. Immediately revoke permission
# 4. Verify no crash
```

---

## Related Files

### Files to Modify
- `app/src/main/java/com/messageai/tactical/modules/ai/work/CasevacWorker.kt` - Add permission checks

### Files to Create
- None (all logic in CasevacWorker)

---

## User Experience Considerations

### Option A: Silent Fallback (Recommended for MVP)
- Permission denied → use (0.0, 0.0) → continue workflow
- User may not even notice
- Mission still created

### Option B: Notify User
```kotlin
if (!hasLocationPermission()) {
    CasevacNotifier.notifyPermissionRequired(
        applicationContext,
        "Location permission required for accurate facility lookup"
    )
    // Then continue with fallback or fail
}
```

### Option C: Request Permission Before Trigger
```kotlin
// In UI before calling CasevacWorker.enqueue()
if (!hasLocationPermission()) {
    requestLocationPermission()
    return
}
CasevacWorker.enqueue(context, chatId, messageId)
```

**Recommendation:** Use Option A for MVP, implement Option C for production

---

## Success Metrics

✅ **Definition of Done:**
1. Permission checked before location access
2. No crashes when permission denied
3. Graceful fallback implemented
4. Warning logged for missing permission
5. Unit tests cover permission scenarios
6. Manual testing confirms no crash
7. Code review approved
8. Clean git commit

---

## References

- Android Location Permissions: https://developer.android.com/training/location/permissions
- WorkManager Best Practices: https://developer.android.com/topic/libraries/architecture/workmanager/advanced
- QC Report: `docs/reviews/BLOCKS_E_F_QC_REPORT.md`

---

**Created by:** QC Agent (Blocks E & F Review)  
**Related Sprint:** Sprint 2 - AI Integration (Security/Bug Fix)  
**Blocks:** F  
**Ticket ID:** BLOCK-F-003

