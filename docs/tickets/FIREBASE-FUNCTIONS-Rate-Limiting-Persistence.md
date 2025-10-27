# TICKET: Persistent Rate Limiting for Firebase Functions

**Status:** 🟡 Backlog  
**Priority:** Medium  
**Type:** Enhancement / Production Readiness  
**Estimated Effort:** 4-6 hours  
**Assignee:** TBD  
**Created:** 2025-10-24

---

## Problem Summary

The `aiRouter` Firebase Function uses in-memory token bucket rate limiting which has limitations:

```typescript
// firebase-functions/functions/src/index.ts lines 278-302
const buckets: Record<string, { tokens: number; lastRefillMs: number }> = {};
```

**Current Limitations:**
- Cold starts reset all rate limit buckets
- Multi-region deployments don't share rate limit state
- Users can bypass limits by triggering cold starts
- No visibility into rate limit metrics

**Impact:**
- Low: Acceptable for MVP but not production-grade
- Risk: Potential for rate limit bypass in edge cases
- Monitoring: No metrics on rate limit hits

---

## Root Cause Analysis

### Technical Details

**Current Implementation:**
- In-memory JavaScript object stores token buckets per UID
- Tokens refill at 10/min with burst capacity of 20
- State is ephemeral and lost on function instance shutdown

**Why This Matters:**
- Cloud Functions scale to zero (cold starts common)
- Multi-region deployments create separate instances
- No shared state between function instances

---

## Solution Options

### Option 1: Firestore-backed Rate Limiting (Recommended)

**What:** Store rate limit state in Firestore with atomic operations

**Pros:**
- Persistent across cold starts
- Shared across all function instances
- Built-in transaction support
- Low latency (<50ms typical)
- Easy to monitor and audit

**Cons:**
- Additional Firestore read/write costs (~$0.18 per million operations)
- Slight latency increase (~30-50ms per request)

**Implementation:**

```typescript
import * as admin from 'firebase-admin';

interface RateLimitBucket {
  tokens: number;
  lastRefillMs: number;
  uid: string;
}

async function checkRateLimit(uid: string): Promise<boolean> {
  const db = admin.firestore();
  const bucketRef = db.collection('rateLimits').doc(uid);
  
  return await db.runTransaction(async (tx) => {
    const doc = await tx.get(bucketRef);
    const now = Date.now();
    
    let bucket: RateLimitBucket;
    if (!doc.exists) {
      bucket = { tokens: BURST, lastRefillMs: now, uid };
    } else {
      bucket = doc.data() as RateLimitBucket;
      // Refill tokens
      const elapsedMin = (now - bucket.lastRefillMs) / 60000;
      const newTokens = Math.floor(elapsedMin * RATE_PER_MIN);
      if (newTokens > 0) {
        bucket.tokens = Math.min(BURST, bucket.tokens + newTokens);
        bucket.lastRefillMs = now;
      }
    }
    
    // Try to consume token
    if (bucket.tokens > 0) {
      bucket.tokens -= 1;
      tx.set(bucketRef, bucket);
      return true;
    }
    return false;
  });
}
```

**Firestore Security Rules:**
```javascript
match /rateLimits/{uid} {
  // Only Cloud Functions can read/write
  allow read, write: if false;
}
```

---

### Option 2: Redis/Memorystore (Higher Performance)

**What:** Use Redis for sub-millisecond rate limiting

**Pros:**
- Ultra-low latency (<5ms)
- Battle-tested for rate limiting
- Rich atomic operations (INCR, EXPIRE)
- Industry standard

**Cons:**
- Requires provisioning Memorystore instance (~$50/month minimum)
- More complex infrastructure
- VPC networking required

**Implementation:**

```typescript
import { createClient } from 'redis';

const redis = createClient({
  socket: { host: process.env.REDIS_HOST }
});

async function checkRateLimit(uid: string): Promise<boolean> {
  const key = `ratelimit:${uid}`;
  const current = await redis.incr(key);
  
  if (current === 1) {
    // First request, set expiry
    await redis.expire(key, 60); // 1 minute window
  }
  
  return current <= RATE_PER_MIN;
}
```

**Cost:** ~$50-100/month for Memorystore basic tier

---

### Option 3: Hybrid Approach (Performance + Cost Balance)

**What:** In-memory cache with Firestore backing

**Pros:**
- Fast path for hot users (in-memory)
- Persistent for cold starts (Firestore)
- Lower Firestore costs (cache hits)

**Cons:**
- More complex implementation
- Eventually consistent (brief windows of bypass)

**Implementation:**
- Check in-memory cache first (fast path)
- Fall back to Firestore on cache miss
- Write back to cache after Firestore update
- Acceptable eventual consistency for MVP→Production

---

## Recommended Solution: Option 1 (Firestore)

### Why Firestore?

1. **Already in use:** No new infrastructure
2. **Cost-effective:** ~$0.36 per 1M AI requests (read+write)
3. **Simple:** Transactions handle all edge cases
4. **Auditable:** Easy to query rate limit violations
5. **Good enough:** 30-50ms latency acceptable for AI operations (which take 2-5s)

### Implementation Steps

1. **Create Firestore collection schema**
   ```typescript
   // Structure: /rateLimits/{uid}
   interface RateLimitDoc {
     tokens: number;
     lastRefillMs: number;
     uid: string;
     createdAt: Timestamp;
   }
   ```

2. **Update aiRouter function**
   - Replace in-memory `buckets` object
   - Use Firestore transaction for atomic token consumption
   - Add error handling for Firestore failures (fallback to allow)

3. **Add Firestore TTL policy**
   ```bash
   # Clean up old documents after 7 days
   gcloud firestore fields ttls update lastRefillMs \
     --collection-group=rateLimits \
     --enable-ttl
   ```

4. **Update security rules**
   - Deny all client access to `/rateLimits`
   - Functions use admin SDK (bypasses rules)

5. **Add monitoring**
   - Log rate limit hits to Cloud Logging
   - Create dashboard for rate limit metrics
   - Alert on unusual patterns

---

## Acceptance Criteria

- [ ] Rate limits persist across cold starts
- [ ] Multi-region deployments share rate limit state
- [ ] Latency increase < 100ms per request
- [ ] All existing tests pass
- [ ] New integration test for rate limit persistence
- [ ] Monitoring dashboard shows rate limit metrics
- [ ] Documentation updated

---

## Testing Checklist

```bash
# 1. Deploy updated function
firebase deploy --only functions:aiRouter

# 2. Test rate limit persistence
# Make 10 requests, trigger cold start, make 11th request
# Expected: 11th request should be rate limited

# 3. Test multi-region (if applicable)
# Make requests from different regions
# Expected: Rate limits apply globally

# 4. Load test
# Simulate 1000 users making requests
# Expected: No rate limit bypass, <100ms overhead

# 5. Verify Firestore documents
# Check /rateLimits collection
# Expected: One document per user with correct token counts
```

---

## Migration Plan

### Phase 1: Add Firestore Backing (Non-Breaking)
- Implement Firestore rate limiting alongside in-memory
- Log discrepancies for monitoring
- Gradual rollout (1% → 10% → 100%)

### Phase 2: Remove In-Memory (Breaking)
- Remove in-memory implementation
- Full Firestore-backed rate limiting
- Monitor performance and costs

### Rollback Plan
- Keep in-memory code commented for 1 sprint
- Can revert via configuration flag if issues

---

## Cost Analysis

**Current (In-Memory):**
- $0 additional cost
- Risk: Rate limit bypass

**Option 1 (Firestore):**
- 1M AI requests/month
- 2M Firestore operations (read+write per request)
- Cost: ~$0.36/month
- **Acceptable:** Negligible vs function compute costs

**Option 2 (Redis):**
- Memorystore basic: ~$50/month
- Better performance but not justified for MVP→Production transition

---

## Performance Impact

**Before (In-Memory):**
- Rate limit check: <1ms
- Total request: 2-5s (LLM dominates)

**After (Firestore):**
- Rate limit check: 30-50ms
- Total request: 2.05-5.05s
- **Impact: <1% increase in total latency**

---

## Related Files

### Files to Modify
- `firebase-functions/functions/src/index.ts` - Replace in-memory buckets
- `firebase-functions/firestore.rules` - Add security rules

### Files to Create
- `firebase-functions/functions/src/rateLimit.ts` - Extracted rate limit logic
- `firebase-functions/functions/test/rateLimit.test.ts` - Unit tests

### Related Documentation
- QC Report: `docs/reviews/BLOCKS_B_B2_QC_REPORT.md`
- Sprint 2 Task Plan: `docs/product/messageai-sprint2-task-plan.md`

---

## Additional Notes

### Alternative: Cloud Tasks for Distributed Rate Limiting
- Use Cloud Tasks queues with max dispatch rates
- More complex but handles rate limiting at queue level
- Consider for future if Firestore has issues

### Monitoring Queries

```javascript
// Count rate limit violations per hour
SELECT COUNT(*) as violations
FROM logs
WHERE jsonPayload.message = "Rate limit exceeded"
GROUP BY TIMESTAMP_TRUNC(timestamp, HOUR)
```

---

## Success Metrics

✅ **Definition of Done:**
1. Rate limits persist across cold starts (verified by test)
2. No rate limit bypass detected in load testing
3. Latency increase < 100ms
4. Firestore costs within budget (<$1/month for MVP traffic)
5. Monitoring dashboard live
6. Clean git commit with descriptive message

---

## References

- [Firebase Rate Limiting Best Practices](https://firebase.google.com/docs/functions/quotas)
- [Firestore Transactions](https://firebase.google.com/docs/firestore/manage-data/transactions)
- [Rate Limiting Strategies](https://cloud.google.com/architecture/rate-limiting-strategies-techniques)
- [Token Bucket Algorithm](https://en.wikipedia.org/wiki/Token_bucket)

---

**Created by:** QC Agent (Blocks B & B2 Review)  
**Related Sprint:** Sprint 2 - AI Integration (Post-MVP Hardening)  
**Blocks:** None (enhancement)  
**Ticket ID:** FIREBASE-FUNCTIONS-001

