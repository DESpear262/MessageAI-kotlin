# TICKET: Add Report Caching to ReportService

**Status:** 🟡 Backlog  
**Priority:** Low  
**Type:** Performance / User Experience  
**Estimated Effort:** 2-3 hours  
**Assignee:** TBD  
**Created:** 2025-10-24

---

## Problem Summary

ReportService regenerates reports on every screen visit, causing:

**Current Behavior:**
```kotlin
// Every time user opens report screen
viewModel.loadSitrep(chatId, "6h")  // ← Calls LangChain service again
```

**Problems:**
1. Slow UX (2-8s wait for same report)
2. Unnecessary LangChain API calls (costs)
3. Wastes OpenAI API quota
4. Poor offline experience

**Impact:**
- Medium: Users wait for same report multiple times
- Cost: Redundant API calls to OpenAI
- UX: Frustrating for frequent report viewers

---

## Solution

Add in-memory cache for recently generated reports.

### Option 1: Simple In-Memory Cache (Recommended)

**What:** Cache reports in ReportService with TTL

**Pros:**
- Simple implementation (~50 lines)
- No persistence overhead
- Automatic cleanup
- Thread-safe with Mutex

**Cons:**
- Cache clears on app restart
- Memory usage increases with reports
- No cross-session persistence

**Implementation:**

```kotlin
class ReportService(private val adapter: LangChainAdapter) {

    // Cache: Map<CacheKey, CachedReport>
    private val cache = mutableMapOf<CacheKey, CachedReport>()
    private val cacheMutex = Mutex()
    
    data class CacheKey(
        val type: String,    // "sitrep", "warnord", etc.
        val chatId: String?, // nullable for templates
        val params: String   // e.g., "6h"
    )
    
    data class CachedReport(
        val markdown: String,
        val timestamp: Long,
        val ttlMs: Long = 5 * 60 * 1000 // 5 minutes
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > ttlMs
    }

    suspend fun generateSITREP(chatId: String, timeWindow: String = "6h"): Result<String> = runCatching {
        val key = CacheKey("sitrep", chatId, timeWindow)
        
        // Check cache first
        cacheMutex.withLock {
            cache[key]?.let { cached ->
                if (!cached.isExpired()) {
                    Log.d(TAG, "Cache hit for SITREP $chatId")
                    return@runCatching cached.markdown
                } else {
                    // Remove expired entry
                    cache.remove(key)
                }
            }
        }
        
        // Cache miss, generate new report
        val payload = mapOf("timeWindow" to timeWindow)
        val ctx = mapOf("chatId" to chatId)
        val res = adapter.post("sitrep/summarize", payload, ctx)
        val markdown = (res.data?.get("content") as? String) ?: error("Missing markdown content")
        
        // Store in cache
        cacheMutex.withLock {
            cache[key] = CachedReport(markdown, System.currentTimeMillis())
            Log.d(TAG, "Cached SITREP for $chatId")
        }
        
        markdown
    }
    
    // Similar for other methods...
    
    // Optional: Manual cache invalidation
    suspend fun invalidateCache(chatId: String? = null) {
        cacheMutex.withLock {
            if (chatId != null) {
                cache.keys.removeAll { it.chatId == chatId }
            } else {
                cache.clear()
            }
        }
    }
    
    // Optional: Periodic cleanup
    suspend fun cleanupExpiredCache() {
        cacheMutex.withLock {
            cache.entries.removeAll { it.value.isExpired() }
        }
    }
}
```

**Configuration:**
```kotlin
companion object {
    private const val SITREP_TTL_MS = 5 * 60 * 1000L      // 5 minutes
    private const val TEMPLATE_TTL_MS = 30 * 60 * 1000L   // 30 minutes
    private const val MAX_CACHE_SIZE = 50                  // Prevent unbounded growth
}
```

---

### Option 2: Room Database Cache (Overkill)

**What:** Persist reports to Room database

**Pros:**
- Survives app restart
- Queryable history
- Can implement "recent reports" feature

**Cons:**
- Much more complex
- Overkill for simple caching
- Database overhead

**Not recommended** for current use case

---

## Recommended Solution: Option 1 (In-Memory Cache)

### Implementation Steps

1. **Add cache fields to ReportService**
   ```kotlin
   private val cache = mutableMapOf<CacheKey, CachedReport>()
   private val cacheMutex = Mutex()
   ```

2. **Update all generate methods**
   - Check cache before API call
   - Store result after successful generation
   - Handle cache expiry

3. **Add cache management methods**
   ```kotlin
   suspend fun invalidateCache(chatId: String?)
   suspend fun cleanupExpiredCache()
   ```

4. **Add periodic cleanup**
   ```kotlin
   // In ViewModel or Application class
   viewModelScope.launch {
       while (isActive) {
           delay(60_000) // Every minute
           reportService.cleanupExpiredCache()
       }
   }
   ```

5. **Add cache hit/miss logging**
   ```kotlin
   Log.d(TAG, "Cache hit for $key")
   Log.d(TAG, "Cache miss for $key, calling LangChain")
   ```

---

## Acceptance Criteria

- [ ] Reports cached in memory with TTL
- [ ] Cache checked before API call
- [ ] Cache miss calls LangChain service
- [ ] Cache hit returns immediately (<100ms)
- [ ] Expired entries automatically removed
- [ ] Thread-safe cache access (Mutex)
- [ ] Manual cache invalidation available
- [ ] Unit tests for cache behavior
- [ ] Cache hit/miss logged for monitoring

---

## Testing Checklist

### Unit Tests

```kotlin
@Test
fun `generateSITREP first call hits API`() = runTest {
    coEvery { adapter.post(any(), any(), any()) } returns mockResponse
    
    reportService.generateSITREP("chat123", "6h")
    
    coVerify(exactly = 1) { adapter.post(any(), any(), any()) }
}

@Test
fun `generateSITREP second call uses cache`() = runTest {
    coEvery { adapter.post(any(), any(), any()) } returns mockResponse
    
    // First call
    reportService.generateSITREP("chat123", "6h")
    
    // Second call (within TTL)
    reportService.generateSITREP("chat123", "6h")
    
    // Should only call API once
    coVerify(exactly = 1) { adapter.post(any(), any(), any()) }
}

@Test
fun `generateSITREP expired cache calls API again`() = runTest {
    coEvery { adapter.post(any(), any(), any()) } returns mockResponse
    
    // First call
    reportService.generateSITREP("chat123", "6h")
    
    // Simulate time passing (mock timestamp or use TestCoroutineScheduler)
    advanceTimeBy(6 * 60 * 1000) // 6 minutes > 5 minute TTL
    
    // Second call (after TTL)
    reportService.generateSITREP("chat123", "6h")
    
    // Should call API twice
    coVerify(exactly = 2) { adapter.post(any(), any(), any()) }
}

@Test
fun `invalidateCache clears specific chatId`() = runTest {
    // Generate reports for two chats
    reportService.generateSITREP("chat123", "6h")
    reportService.generateSITREP("chat456", "6h")
    
    // Invalidate one
    reportService.invalidateCache("chat123")
    
    // chat123 should hit API, chat456 should use cache
    reportService.generateSITREP("chat123", "6h")
    reportService.generateSITREP("chat456", "6h")
    
    coVerify(exactly = 3) { adapter.post(any(), any(), any()) } // 2 initial + 1 for chat123
}
```

### Integration Test

```bash
# 1. Generate SITREP
# 2. Go back and reopen report screen
# 3. Verify instant load (no loading spinner)
# 4. Check logcat for "Cache hit" message
# 5. Wait 5+ minutes
# 6. Reopen report screen
# 7. Verify loading spinner (cache expired)
# 8. Check logcat for "Cache miss" message
```

---

## Performance Impact

**Before (No Cache):**
- Every screen visit: 2-8s LangChain call
- Cost: $X per 1K API calls

**After (With Cache):**
- First visit: 2-8s (cache miss)
- Subsequent visits (within 5 min): <100ms (cache hit)
- Cost reduction: ~80-90% (assuming users view reports multiple times)

**Memory Usage:**
- Per report: ~5-50KB (markdown string)
- Max 50 reports: ~250KB-2.5MB (negligible)

---

## Related Files

### Files to Modify
- `app/src/main/java/com/messageai/tactical/modules/reporting/ReportService.kt` - Add caching

### Files to Create
- `app/src/test/java/com/messageai/tactical/modules/reporting/ReportServiceCacheTest.kt` - Cache tests

### Related Documentation
- QC Report: `docs/reviews/BLOCKS_C_D_QC_REPORT.md`

---

## Cache Invalidation Strategy

**When to invalidate:**
1. New messages in chat (chat activity invalidates SITREP)
2. User manually requests "refresh"
3. App goes to background for extended period

**How to trigger:**
```kotlin
// In ViewModel when new messages received
messageFlow.collect { newMessage ->
    reportService.invalidateCache(chatId = newMessage.chatId)
}

// Manual refresh button
Button(onClick = {
    viewModelScope.launch {
        reportService.invalidateCache(chatId)
        viewModel.loadSitrep(chatId) // Force regeneration
    }
}) {
    Text("Refresh Report")
}
```

---

## Success Metrics

✅ **Definition of Done:**
1. In-memory cache implemented
2. TTL-based expiry working
3. Cache hit/miss logged
4. Manual invalidation available
5. Unit tests pass
6. Performance improvement verified (< 100ms for cache hits)
7. Clean git commit

---

## Future Enhancements

- Persistent cache (Room database)
- Report history feature
- Cache size limits (LRU eviction)
- User-configurable TTL
- Cache warming (pre-generate common reports)

---

## References

- [Kotlin Mutex](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.sync/-mutex/)
- [Caching Strategies](https://en.wikipedia.org/wiki/Cache_replacement_policies)

---

**Created by:** QC Agent (Blocks C & D Review)  
**Related Sprint:** Sprint 2 - AI Integration (Performance)  
**Blocks:** None (enhancement)  
**Ticket ID:** BLOCK-D-001

