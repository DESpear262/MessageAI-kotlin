# TICKET: Add Error Propagation in GeoService.analyzeChatThreats

**Status:** 🟡 Backlog  
**Priority:** Low  
**Type:** Code Quality / Bug Fix  
**Estimated Effort:** 1 hour  
**Assignee:** TBD  
**Created:** 2025-10-24

---

## Problem Summary

`GeoService.analyzeChatThreats()` swallows errors silently, making debugging difficult:

```kotlin
// GeoService.kt lines 62-93
fun analyzeChatThreats(chatId: String, maxMessages: Int = 100, onComplete: ((Int) -> Unit)? = null) {
    val locTask = fused.lastLocation
    locTask.addOnSuccessListener { loc ->
        CoroutineScope(Dispatchers.IO).launch {
            val result = aiService.summarizeThreats(chatId, maxMessages)
            val items: List<Map<String, Any?>>? = result.getOrNull()
            
            // If result fails, onComplete still called with 0
            // No error callback, no logging, no UI feedback
            val count = items?.let { ... } ?: 0
            onComplete?.invoke(count)
        }
    }
}
```

**Problems:**
1. AI call failures are silent (user sees no feedback)
2. Location failures are ignored
3. No way for caller to know if operation succeeded
4. Debugging production issues is difficult

**Impact:**
- Low: Feature degrades gracefully but silently
- UX: Users don't know why threats aren't extracted
- Debugging: Hard to diagnose production failures

---

## Root Cause Analysis

### Why Silent Failure Exists

**MVP Tradeoff:**
- Quick implementation prioritized over robust error handling
- Callback-based API makes error propagation awkward
- No clear UX for error states

**Technical Debt:**
- Should return Result or have error callback
- Should log errors for debugging
- Should show user-friendly error messages

---

## Solution Options

### Option 1: Add Error Callback (Quick Fix)

**What:** Add optional error callback parameter

**Pros:**
- Simple change
- Backward compatible (optional parameter)
- Caller decides how to handle errors

**Cons:**
- Still callback-based (not idiomatic Kotlin)
- Two callbacks make API clunky

**Implementation:**

```kotlin
fun analyzeChatThreats(
    chatId: String,
    maxMessages: Int = 100,
    onComplete: ((Int) -> Unit)? = null,
    onError: ((Throwable) -> Unit)? = null  // ← New parameter
) {
    val locTask = fused.lastLocation
    locTask.addOnSuccessListener { loc ->
        val fallbackLat = loc?.latitude
        val fallbackLon = loc?.longitude
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = aiService.summarizeThreats(chatId, maxMessages)
                result.onSuccess { items ->
                    val saved = persistThreats(items, fallbackLat, fallbackLon)
                    onComplete?.invoke(saved)
                }.onFailure { error ->
                    Log.e(TAG, "AI threat extraction failed", error)
                    onError?.invoke(error)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error in analyzeChatThreats", e)
                onError?.invoke(e)
            }
        }
    }.addOnFailureListener { error ->
        Log.e(TAG, "Location fetch failed", error)
        onError?.invoke(error)
    }
}

// Call site
geoService.analyzeChatThreats(
    chatId = chatId,
    onComplete = { count -> showToast("Extracted $count threats") },
    onError = { error -> showToast("Failed: ${error.message}") }
)
```

---

### Option 2: Return Result Type (Recommended)

**What:** Make method suspend and return Result

**Pros:**
- Idiomatic Kotlin
- Explicit success/failure
- Easier to test
- Composable with other coroutines

**Cons:**
- Breaking API change (requires updating call sites)
- Part of larger refactoring (async standardization)

**Implementation:**

```kotlin
suspend fun analyzeChatThreats(
    chatId: String,
    maxMessages: Int = 100
): Result<Int> = withContext(Dispatchers.IO) {
    runCatching {
        // Get location
        val loc = fused.lastLocation.await()
        val fallbackLat = loc?.latitude
        val fallbackLon = loc?.longitude
        
        // Call AI
        val result = aiService.summarizeThreats(chatId, maxMessages)
        val items = result.getOrThrow()  // Propagate errors
        
        // Persist threats
        var saved = 0
        items.forEach { threatMap ->
            val summary = threatMap["summary"]?.toString() ?: return@forEach
            val severity = (threatMap["severity"] as? Number)?.toInt() ?: 3
            val radiusM = (threatMap["radiusM"] as? Number)?.toInt() ?: DEFAULT_RADIUS_M
            val geo = threatMap["geo"] as? Map<*, *>
            val lat = (geo?.get("lat") as? Number)?.toDouble() ?: fallbackLat
            val lon = (geo?.get("lon") as? Number)?.toDouble() ?: fallbackLon
            
            val data = hashMapOf(
                "summary" to summary,
                "severity" to severity,
                "confidence" to ((threatMap["confidence"] as? Number)?.toDouble() ?: 0.75),
                "geo" to if (lat != null && lon != null) mapOf("lat" to lat, "lon" to lon) else null,
                "radiusM" to radiusM,
                "ts" to Timestamp.now()
            ).filterValues { it != null }
            
            firestore.collection(THREATS_COLLECTION).add(data).await()
            saved += 1
        }
        saved
    }
}

// Call site
viewModelScope.launch {
    geoService.analyzeChatThreats(chatId, 100)
        .onSuccess { count -> showToast("Extracted $count threats") }
        .onFailure { error -> 
            Log.e(TAG, "Threat analysis failed", error)
            showToast("Failed to extract threats: ${error.message}")
        }
}
```

---

## Recommended Solution: Option 2 (Result Type)

**Why:**
- More idiomatic Kotlin
- Part of larger async standardization effort
- Better testability
- Clearer error handling

**Depends on:** Ticket BLOCK-C-001 (Standardize Async Patterns)

**Alternative:** If BLOCK-C-001 is deferred, implement Option 1 as quick fix

---

## Implementation Steps

1. **Convert to suspend function**
   ```kotlin
   suspend fun analyzeChatThreats(chatId: String, maxMessages: Int): Result<Int>
   ```

2. **Add structured logging**
   ```kotlin
   Log.e(TAG, "AI threat extraction failed: $error", error)
   ```

3. **Update call sites**
   ```kotlin
   // In ViewModel/Worker
   viewModelScope.launch {
       geoService.analyzeChatThreats(chatId)
           .onSuccess { count -> updateUI(count) }
           .onFailure { error -> showError(error) }
   }
   ```

4. **Add unit tests**
   ```kotlin
   @Test
   fun analyzeChatThreats_aiFailure_returnsFailure() = runTest {
       // Mock AIService to return failure
       coEvery { aiService.summarizeThreats(any(), any()) } returns Result.failure(Exception("API error"))
       
       val result = geoService.analyzeChatThreats("chat123", 100)
       
       assertTrue(result.isFailure)
       assertEquals("API error", result.exceptionOrNull()?.message)
   }
   ```

---

## Acceptance Criteria

- [ ] Errors properly propagated to caller
- [ ] Structured logging for all error cases
- [ ] Result type returned (success count or error)
- [ ] Location errors handled gracefully
- [ ] AI errors handled gracefully
- [ ] Firestore errors handled gracefully
- [ ] Unit tests for error scenarios
- [ ] Call sites updated to handle errors
- [ ] User-friendly error messages in UI

---

## Testing Checklist

### Unit Tests

```kotlin
@Test
fun analyzeChatThreats_success_returnsCount() = runTest {
    // Mock successful flow
    coEvery { fused.lastLocation.await() } returns mockLocation
    coEvery { aiService.summarizeThreats(any(), any()) } returns Result.success(mockThreats)
    
    val result = geoService.analyzeChatThreats("chat123", 100)
    
    assertTrue(result.isSuccess)
    assertEquals(3, result.getOrNull())
}

@Test
fun analyzeChatThreats_locationFailure_stillSucceeds() = runTest {
    // Location fails but should continue with null fallback
    coEvery { fused.lastLocation.await() } throws Exception("Location unavailable")
    coEvery { aiService.summarizeThreats(any(), any()) } returns Result.success(mockThreats)
    
    val result = geoService.analyzeChatThreats("chat123", 100)
    
    assertTrue(result.isSuccess)
}

@Test
fun analyzeChatThreats_aiFailure_returnsFailure() = runTest {
    coEvery { aiService.summarizeThreats(any(), any()) } returns Result.failure(Exception("AI error"))
    
    val result = geoService.analyzeChatThreats("chat123", 100)
    
    assertTrue(result.isFailure)
}
```

### Integration Test
```bash
# 1. Disconnect from internet
# 2. Trigger threat analysis
# 3. Verify error message shown to user
# 4. Check logcat for error details
```

---

## Related Files

### Files to Modify
- `app/src/main/java/com/messageai/tactical/modules/geo/GeoService.kt` - Add error propagation
- All call sites - Update to handle Result

### Files to Create
- `app/src/test/java/com/messageai/tactical/modules/geo/GeoServiceTest.kt` - Error scenario tests

### Related Documentation
- QC Report: `docs/reviews/BLOCKS_C_D_QC_REPORT.md`
- Related Ticket: BLOCK-C-001 (Async standardization)

---

## Error Message Guidelines

**User-Facing Messages:**
```kotlin
when (error) {
    is IOException -> "Network error. Check your connection."
    is SecurityException -> "Location permission required."
    is FirebaseException -> "Unable to save threats. Try again later."
    else -> "Failed to analyze threats: ${error.message}"
}
```

**Logging:**
```kotlin
Log.e(TAG, "analyzeChatThreats failed", error)
// Include context
Log.e(TAG, "chatId=$chatId, maxMessages=$maxMessages", error)
```

---

## Success Metrics

✅ **Definition of Done:**
1. Method returns Result type
2. All errors logged with context
3. Caller can handle success/failure explicitly
4. Unit tests for all error paths
5. User sees appropriate error messages
6. No silent failures
7. Clean git commit

---

## References

- [Kotlin Result API](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-result/)
- [Error Handling Best Practices](https://kotlinlang.org/docs/exception-handling.html)
- [Android Logging](https://developer.android.com/reference/android/util/Log)

---

**Created by:** QC Agent (Blocks C & D Review)  
**Related Sprint:** Sprint 2 - AI Integration (Post-MVP Code Quality)  
**Blocks:** None (bug fix)  
**Ticket ID:** BLOCK-C-002

