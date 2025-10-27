# TICKET: Add Structured Error Logging to CasevacWorker

**Status:** 🟡 Backlog  
**Priority:** Medium  
**Type:** Code Quality / Observability  
**Estimated Effort:** 1-2 hours  
**Assignee:** TBD  
**Created:** 2025-10-24

---

## Problem Summary

`CasevacWorker` swallows all exceptions without logging:

```kotlin
// CasevacWorker.kt lines 67-69
} catch (_: Exception) {
    Result.retry()
}
```

**Problems:**
1. No error information logged
2. Can't debug production failures
3. No visibility into retry reasons
4. No metrics for failure rates
5. Silent failures hard to diagnose

**Impact:**
- Medium: Debugging production issues difficult
- Observability: No failure metrics
- Maintainability: Hard to identify root causes

---

## Solution

Add structured logging with contextual information.

### Implementation

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
        val chatId = inputData.getString(KEY_CHAT_ID)
        val messageId = inputData.getString(KEY_MESSAGE_ID)
        
        // Input validation with logging
        if (chatId == null) {
            Log.e(TAG, "CasevacWorker started without chatId")
            return@withContext Result.failure()
        }
        
        Log.i(TAG, "CASEVAC workflow started: chatId=$chatId, messageId=$messageId, runAttempt=${runAttemptCount}")
        
        try {
            CasevacNotifier.notifyStart(applicationContext, chatId)
            
            // Step 1: Generate 9-line
            Log.d(TAG, "Step 1/4: Generating MEDEVAC template for chatId=$chatId")
            val templateStart = System.currentTimeMillis()
            val templateResult = ai.generateTemplate(chatId, type = "MEDEVAC", maxMessages = 50)
            val templateDuration = System.currentTimeMillis() - templateStart
            
            if (templateResult.isFailure) {
                Log.e(TAG, "Step 1 failed: MEDEVAC template generation error", templateResult.exceptionOrNull())
                throw templateResult.exceptionOrNull() ?: Exception("Template generation failed")
            }
            Log.d(TAG, "Step 1 complete: MEDEVAC template generated in ${templateDuration}ms")

            // Step 2: Find nearest facility
            Log.d(TAG, "Step 2/4: Finding nearest facility")
            val locStart = System.currentTimeMillis()
            val loc = fused.lastLocation.awaitNullable()
            val lat = loc?.latitude ?: 0.0
            val lon = loc?.longitude ?: 0.0
            
            if (loc == null) {
                Log.w(TAG, "Location unavailable, using fallback coordinates (0.0, 0.0)")
            } else {
                Log.d(TAG, "Location obtained: lat=$lat, lon=$lon")
            }
            
            val facility = facilities.nearest(lat, lon)
            val facilityDuration = System.currentTimeMillis() - locStart
            
            if (facility == null) {
                Log.w(TAG, "No facilities found near ($lat, $lon)")
            } else {
                Log.d(TAG, "Step 2 complete: Nearest facility found: ${facility.name} (${facility.id}) in ${facilityDuration}ms")
            }

            // Step 3: Create mission
            Log.d(TAG, "Step 3/4: Creating CASEVAC mission")
            val missionStart = System.currentTimeMillis()
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
            val missionDuration = System.currentTimeMillis() - missionStart
            Log.d(TAG, "Step 3 complete: Mission created with ID=$createdId in ${missionDuration}ms")
            
            // Step 4: Update casualties and complete
            Log.d(TAG, "Step 4/4: Updating casualties and completing mission")
            missions.incrementCasevacCasualties(chatId, delta = 1)
            missions.updateMission(createdId, mapOf("status" to "done"))
            missions.archiveIfCompleted(createdId)
            Log.d(TAG, "Step 4 complete: Mission updated and archived")

            CasevacNotifier.notifyComplete(applicationContext, facility?.name)
            
            val totalDuration = templateDuration + facilityDuration + missionDuration
            Log.i(TAG, "CASEVAC workflow completed successfully: chatId=$chatId, missionId=$createdId, totalDuration=${totalDuration}ms")
            
            Result.success()
            
        } catch (e: SecurityException) {
            // Location permission denied
            Log.e(TAG, "CASEVAC workflow failed: Location permission denied", e)
            logWorkflowError(chatId, messageId, "SecurityException", e.message ?: "Unknown", runAttemptCount)
            Result.failure()  // Don't retry permission errors
            
        } catch (e: IOException) {
            // Network error
            Log.e(TAG, "CASEVAC workflow failed: Network error (will retry)", e)
            logWorkflowError(chatId, messageId, "IOException", e.message ?: "Unknown", runAttemptCount)
            Result.retry()
            
        } catch (e: Exception) {
            // Generic error
            Log.e(TAG, "CASEVAC workflow failed: Unexpected error (will retry)", e)
            logWorkflowError(chatId, messageId, e::class.java.simpleName, e.message ?: "Unknown", runAttemptCount)
            Result.retry()
        }
    }

    /**
     * Logs structured error information for analytics/monitoring
     */
    private fun logWorkflowError(chatId: String?, messageId: String?, errorType: String, errorMessage: String, attemptCount: Int) {
        val errorLog = buildString {
            append("CASEVAC_ERROR|")
            append("chatId=$chatId|")
            append("messageId=$messageId|")
            append("errorType=$errorType|")
            append("errorMessage=$errorMessage|")
            append("attemptCount=$attemptCount|")
            append("timestamp=${System.currentTimeMillis()}")
        }
        Log.e(TAG, errorLog)
        
        // Optional: Send to analytics/crashlytics
        // FirebaseCrashlytics.getInstance().log(errorLog)
    }

    companion object {
        private const val TAG = "CasevacWorker"
        private const val KEY_CHAT_ID = "chatId"
        private const val KEY_MESSAGE_ID = "messageId"

        fun enqueue(context: Context, chatId: String, messageId: String?) {
            Log.i(TAG, "Enqueueing CASEVAC workflow: chatId=$chatId, messageId=$messageId")
            
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
    }
}

private suspend fun FusedLocationProviderClient.awaitNullable(): android.location.Location? =
    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        lastLocation.addOnSuccessListener { loc -> cont.resume(loc) {} }
            .addOnFailureListener { e ->
                Log.w("CasevacWorker", "Location fetch failed", e)
                cont.resume(null) {}
            }
    }
```

**Additional Import:**
```kotlin
import android.util.Log
import java.io.IOException
```

---

## Logging Levels

### INFO (Success/Major Events)
- Workflow started
- Workflow completed
- Total duration

### DEBUG (Step Progress)
- Each step start/completion
- Timing for each step
- Intermediate results

### WARN (Non-Fatal Issues)
- Location unavailable (using fallback)
- No facilities found
- Permission not granted (but workflow continues)

### ERROR (Failures)
- Template generation failed
- Network errors
- Unexpected exceptions
- Permission denied (fatal)

---

## Structured Log Format

**Success Log:**
```
I/CasevacWorker: CASEVAC workflow completed successfully: chatId=chat-123, missionId=mission-456, totalDuration=4523ms
```

**Error Log:**
```
E/CasevacWorker: CASEVAC_ERROR|chatId=chat-123|messageId=msg-789|errorType=IOException|errorMessage=Network timeout|attemptCount=2|timestamp=1729785600000
```

**Benefits:**
- Easily parseable by log aggregators
- Filterable by chatId or error type
- Includes retry attempt count
- Includes timestamp for correlation

---

## Integration with Monitoring

### Optional Enhancements

**Firebase Crashlytics:**
```kotlin
import com.google.firebase.crashlytics.FirebaseCrashlytics

private fun logWorkflowError(...) {
    // ... existing logging
    
    // Send to Crashlytics
    FirebaseCrashlytics.getInstance().apply {
        setCustomKey("casevac_chatId", chatId ?: "unknown")
        setCustomKey("casevac_attemptCount", attemptCount)
        recordException(Exception("CASEVAC workflow error: $errorType - $errorMessage"))
    }
}
```

**Custom Analytics Event:**
```kotlin
import com.google.firebase.analytics.FirebaseAnalytics

private fun logWorkflowCompletion(chatId: String, duration: Long, success: Boolean) {
    FirebaseAnalytics.getInstance(applicationContext).logEvent("casevac_workflow_complete") {
        param("chat_id", chatId)
        param("duration_ms", duration)
        param("success", if (success) 1 else 0)
    }
}
```

---

## Acceptance Criteria

- [ ] All workflow steps logged with DEBUG level
- [ ] Success logged with INFO level and total duration
- [ ] Errors logged with ERROR level and context
- [ ] Structured error log format implemented
- [ ] Error types differentiated (SecurityException, IOException, generic)
- [ ] Retry attempts logged
- [ ] Location fallback logged
- [ ] No sensitive information in logs (PII, etc.)
- [ ] Log levels appropriate (not too verbose)
- [ ] Code review approved

---

## Testing Checklist

### Manual Tests
```bash
# Test 1: Successful workflow
# 1. Trigger CASEVAC
# 2. Check logcat for:
#    - "CASEVAC workflow started"
#    - Step 1-4 debug logs
#    - "CASEVAC workflow completed successfully"
#    - Total duration logged

adb logcat | grep CasevacWorker

# Test 2: Network failure
# 1. Turn on airplane mode
# 2. Trigger CASEVAC
# 3. Check logcat for:
#    - Error log with "IOException"
#    - Retry attempt logged

# Test 3: Permission denied
# 1. Revoke location permission
# 2. Trigger CASEVAC
# 3. Check logcat for:
#    - "Location unavailable, using fallback"
#    - Or "SecurityException" if fatal

# Test 4: No facilities
# 1. Clear facilities collection
# 2. Trigger CASEVAC
# 3. Check logcat for:
#    - "No facilities found near..."
```

---

## Log Aggregation Setup (Optional)

For production monitoring, integrate with:
- **Firebase Crashlytics** - Crash/error tracking
- **Firebase Analytics** - Success/failure metrics
- **Google Cloud Logging** - Centralized log aggregation
- **Datadog / New Relic** - APM monitoring

---

## Success Metrics

✅ **Definition of Done:**
1. All major workflow steps logged
2. Errors logged with full context
3. Structured log format implemented
4. Different error types handled separately
5. No excessive logging (performance impact)
6. Manual testing confirms useful logs
7. Code review approved
8. Clean git commit

---

## Performance Considerations

**Logging Overhead:**
- DEBUG logs: ~0.1ms each (negligible)
- String interpolation: Use `"text=$value"` (lazy evaluation)
- Avoid logging large objects (just IDs)

**Production vs Development:**
```kotlin
// Optional: Disable DEBUG logs in production
if (BuildConfig.DEBUG) {
    Log.d(TAG, "Step details...")
}
// Always keep INFO, WARN, ERROR
```

---

## References

- Android Logging Best Practices: https://developer.android.com/studio/debug/am-logcat
- Structured Logging: https://www.datadoghq.com/knowledge-center/structured-logging/
- QC Report: `docs/reviews/BLOCKS_E_F_QC_REPORT.md`

---

**Created by:** QC Agent (Blocks E & F Review)  
**Related Sprint:** Sprint 2 - AI Integration (Observability)  
**Blocks:** F  
**Ticket ID:** BLOCK-F-004

