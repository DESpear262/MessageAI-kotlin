# TICKET: Standardize Async Patterns in GeoService

**Status:** 🟡 Backlog  
**Priority:** Medium  
**Type:** Code Quality / Refactoring  
**Estimated Effort:** 2-3 hours  
**Assignee:** TBD  
**Created:** 2025-10-24

---

## Problem Summary

GeoService uses mixed async patterns that make the code harder to understand and maintain:

```kotlin
// Pattern 1: Google Tasks API
fun summarizeThreatsNear(...) {
    firestore.collection(THREATS_COLLECTION)
        .get()
        .addOnSuccessListener { snap -> ... }
}

// Pattern 2: Coroutines
fun analyzeChatThreats(...) {
    CoroutineScope(Dispatchers.IO).launch {
        val result = aiService.summarizeThreats(chatId, maxMessages)
        // ...
    }
}
```

**Current Issues:**
- Two different async APIs for similar operations
- Harder to test (Tasks API less mockable than coroutines)
- Inconsistent error handling patterns
- Code style inconsistency

**Impact:**
- Low: Code works but is harder to maintain
- Maintainability: Future developers need to understand both patterns
- Testing: Mixed patterns complicate unit testing

---

## Root Cause Analysis

### Why Mixed Patterns Exist

**Historical Context:**
- Firestore SDK uses Tasks API by default
- AIService uses modern coroutines (suspend functions)
- GeoService written quickly for MVP, mixed both

**Technical Debt:**
- Firestore can be wrapped in `suspendCoroutine` for consistency
- All async operations should use coroutines for uniformity

---

## Solution

### Option 1: Standardize on Coroutines (Recommended)

**What:** Convert all Tasks API calls to suspend functions

**Pros:**
- Modern Kotlin approach
- Better testability
- Consistent error handling (Result/try-catch)
- Easier to compose operations

**Cons:**
- Requires wrapping Firestore calls
- Slight boilerplate increase

**Implementation:**

```kotlin
// Extension function for Firestore
suspend fun <T> Task<T>.await(): T = suspendCoroutine { continuation ->
    addOnSuccessListener { result ->
        continuation.resume(result)
    }
    addOnFailureListener { exception ->
        continuation.resumeWithException(exception)
    }
}

// Updated GeoService methods
suspend fun summarizeThreatsNear(
    latitude: Double,
    longitude: Double,
    maxMiles: Double = 500.0,
    limit: Int = 50
): Result<List<Threat>> = runCatching {
    val snap = firestore.collection(THREATS_COLLECTION)
        .orderBy("ts")
        .limit(500)
        .get()
        .await()  // ← Await instead of callback
    
    val nowMs = System.currentTimeMillis()
    val threats = snap.documents.mapNotNull { doc ->
        // ... same mapping logic
    }
    
    val fresh = threats.filter { t -> (nowMs - t.ts) <= EIGHT_HOURS_MS }
    val within = fresh.filter { t -> milesBetween(latitude, longitude, t.lat, t.lon) <= maxMiles }
    within.sortedWith(compareByDescending<Threat> { it.severity }.thenByDescending { it.ts }).take(limit)
}

// Call site with coroutines
viewModelScope.launch {
    geoService.summarizeThreatsNear(lat, lon, 500.0, 50)
        .onSuccess { threats -> updateUI(threats) }
        .onFailure { error -> showError(error) }
}
```

**Benefits:**
- All async operations follow same pattern
- Easier to test with `runTest` and `TestCoroutineDispatcher`
- Cleaner call sites (no nested callbacks)
- Result type for explicit error handling

---

### Option 2: Standardize on Tasks API (Not Recommended)

**What:** Convert coroutines to Tasks API

**Pros:**
- Native Firestore pattern
- No wrapper functions needed

**Cons:**
- Old-fashioned approach
- Harder to test
- Callback hell for complex operations
- Doesn't work well with suspend functions

**Not recommended** - Coroutines are the future of Kotlin async

---

## Recommended Solution: Option 1 (Coroutines)

### Implementation Steps

1. **Create Firestore extension**
   ```kotlin
   // New file: app/src/main/java/com/messageai/tactical/util/TaskExt.kt
   import com.google.android.gms.tasks.Task
   import kotlin.coroutines.resume
   import kotlin.coroutines.resumeWithException
   import kotlin.coroutines.suspendCoroutine
   
   suspend fun <T> Task<T>.await(): T = suspendCoroutine { continuation ->
       addOnSuccessListener { result -> continuation.resume(result) }
       addOnFailureListener { exception -> continuation.resumeWithException(exception) }
   }
   ```

2. **Update GeoService methods**
   ```kotlin
   // Make all public methods suspend functions
   suspend fun summarizeThreatsNear(...): Result<List<Threat>>
   suspend fun checkGeofenceEnter(...): Result<List<Threat>>
   
   // Keep analyzeChatThreats as is (already uses coroutines)
   ```

3. **Update call sites**
   ```kotlin
   // In ViewModels, Workers, etc.
   viewModelScope.launch {
       geoService.summarizeThreatsNear(lat, lon)
           .onSuccess { threats -> ... }
           .onFailure { error -> ... }
   }
   ```

4. **Update tests**
   ```kotlin
   @Test
   fun testSummarizeThreatsNear() = runTest {
       val result = geoService.summarizeThreatsNear(0.0, 0.0)
       assertTrue(result.isSuccess)
   }
   ```

---

## Acceptance Criteria

- [ ] All GeoService public methods use suspend functions
- [ ] No `addOnSuccessListener` callbacks remain in GeoService
- [ ] All call sites updated to use coroutines
- [ ] Existing functionality preserved (no behavior changes)
- [ ] Unit tests pass
- [ ] Manual testing confirms threat alerts still work
- [ ] Code compiles without warnings

---

## Testing Checklist

### Unit Tests
```kotlin
class GeoServiceTest {
    @Test
    fun summarizeThreatsNear_returnsFilteredThreats() = runTest {
        // Mock Firestore
        val mockFirestore = mockk<FirebaseFirestore>()
        val mockCollection = mockk<CollectionReference>()
        val mockQuery = mockk<Query>()
        val mockTask = mockk<Task<QuerySnapshot>>()
        
        every { mockFirestore.collection("threats") } returns mockCollection
        every { mockCollection.orderBy("ts") } returns mockQuery
        every { mockQuery.limit(500) } returns mockQuery
        every { mockQuery.get() } returns mockTask
        
        // Stub await() to return test data
        coEvery { mockTask.await() } returns mockQuerySnapshot
        
        val geoService = GeoService(context, mockFirestore, auth, aiService)
        val result = geoService.summarizeThreatsNear(0.0, 0.0)
        
        assertTrue(result.isSuccess)
        assertEquals(3, result.getOrNull()?.size)
    }
}
```

### Integration Test
```bash
# 1. Run app and trigger threat summary
# 2. Verify threats appear in UI
# 3. Verify no crashes or errors in logcat
```

---

## Related Files

### Files to Create
- `app/src/main/java/com/messageai/tactical/util/TaskExt.kt` - Firestore Task extensions

### Files to Modify
- `app/src/main/java/com/messageai/tactical/modules/geo/GeoService.kt` - Convert to suspend functions
- All call sites (ViewModels, Workers, etc.) - Update to use coroutines

### Files to Test
- `app/src/test/java/com/messageai/tactical/modules/geo/GeoServiceTest.kt` - New unit tests

### Related Documentation
- QC Report: `docs/reviews/BLOCKS_C_D_QC_REPORT.md`

---

## Additional Notes

### Firestore Coroutine Extensions

Alternatively, use existing library:
```kotlin
// Add to dependencies
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

// Use built-in await()
import kotlinx.coroutines.tasks.await
val snapshot = firestore.collection("threats").get().await()
```

**Recommendation:** Use the library (kotlinx-coroutines-play-services) instead of custom extension

### Migration Strategy

**Phase 1:** Add TaskExt.kt without breaking changes
**Phase 2:** Create new suspend function versions alongside old ones
**Phase 3:** Update call sites one by one
**Phase 4:** Remove old callback-based methods

### Error Handling

```kotlin
// Before (Tasks)
firestore.get()
    .addOnSuccessListener { ... }
    .addOnFailureListener { error -> Log.e(...) }

// After (Coroutines)
try {
    val result = firestore.get().await()
    // ...
} catch (e: Exception) {
    Log.e(TAG, "Firestore error", e)
    Result.failure(e)
}

// Or with Result
suspend fun fetchThreats(): Result<List<Threat>> = runCatching {
    val snap = firestore.get().await()
    // ...
}
```

---

## Success Metrics

✅ **Definition of Done:**
1. All GeoService methods use suspend functions
2. No callback-based async in GeoService
3. All call sites updated
4. Tests pass (unit + integration)
5. No behavior changes (same functionality)
6. Code review approved
7. Clean git commit

---

## Performance Impact

**Before:**
- Callback overhead: ~negligible
- Memory: Callbacks held in memory until completion

**After:**
- Coroutine overhead: ~negligible (same performance)
- Memory: Better (coroutines more efficient)
- Cancellation: Easier (coroutine scopes)

**Net impact: Neutral to positive**

---

## References

- [Kotlin Coroutines Guide](https://kotlinlang.org/docs/coroutines-guide.html)
- [Kotlinx Coroutines Play Services](https://github.com/Kotlin/kotlinx.coroutines/tree/master/integration/kotlinx-coroutines-play-services)
- [Firestore with Coroutines](https://firebase.google.com/docs/firestore/query-data/get-data#kotlin+ktx_1)
- [Testing Coroutines](https://kotlinlang.org/docs/coroutines-testing.html)

---

**Created by:** QC Agent (Blocks C & D Review)  
**Related Sprint:** Sprint 2 - AI Integration (Post-MVP Code Quality)  
**Blocks:** None (code quality improvement)  
**Ticket ID:** BLOCK-C-001

