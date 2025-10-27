# TICKET: Add Circuit Breaker to Firebase Functions Proxy

**Status:** 🟡 Backlog  
**Priority:** Low  
**Type:** Enhancement / Reliability  
**Estimated Effort:** 3-4 hours  
**Assignee:** TBD  
**Created:** 2025-10-24

---

## Problem Summary

The `aiRouter` Firebase Function forwards requests to the LangChain service without circuit breaker protection:

```typescript
// firebase-functions/functions/src/index.ts lines 346-369
async function forwardToLangChain(path: string, envelope: Envelope, timeoutMs: number): Promise<Response> {
  // Direct fetch without failure tracking
  const res = await fetchFn(`${baseUrl}/${path}`, { ... });
  return res;
}
```

**Current Behavior:**
- Every request attempts to call LangChain service
- No failure tracking or backoff
- Repeated failures can cascade to all users
- No automatic recovery mechanism

**Impact:**
- Low: Acceptable for MVP
- Risk: Cascading failures if LangChain service is down
- UX: Slow failures (wait for full timeout on every request)

---

## Root Cause Analysis

### Technical Details

**What is a Circuit Breaker?**

A circuit breaker monitors failures and "opens" (stops forwarding) when failure rate exceeds threshold, preventing cascade failures:

```
States:
┌─────────┐  success  ┌────────┐  threshold  ┌──────┐
│ CLOSED  │ ────────> │  OPEN  │ ──────────> │ HALF │
│(normal) │           │(failing)│             │ OPEN │
└─────────┘           └────────┘             └──────┘
     ^                                            |
     └────────────────────────────────────────────┘
                   recovery timeout
```

**Why It Matters:**
- Upstream service outages shouldn't take down the proxy
- Fail fast instead of waiting for timeouts
- Automatic recovery when service returns

**Example Scenario Without Circuit Breaker:**
1. LangChain service goes down
2. 100 concurrent requests all wait 30s for timeout
3. Firebase Function instances overwhelmed
4. All users experience 30s delays
5. Costs spike (function execution time)

**With Circuit Breaker:**
1. First 5 requests fail (threshold reached)
2. Circuit opens, subsequent requests fail immediately
3. Users get fast failure response
4. After 60s, circuit tries half-open (test request)
5. If successful, resume normal operation

---

## Solution Options

### Option 1: Simple Circuit Breaker Implementation (Recommended)

**What:** Lightweight in-memory circuit breaker

**Pros:**
- Simple to implement (~100 lines)
- No external dependencies
- Fast (in-memory state)
- Good enough for single-region deployment

**Cons:**
- State is per-function instance (not shared)
- Resets on cold start

**Implementation:**

```typescript
enum CircuitState {
  CLOSED = 'CLOSED',     // Normal operation
  OPEN = 'OPEN',         // Failing, reject requests
  HALF_OPEN = 'HALF_OPEN' // Testing recovery
}

interface CircuitBreakerConfig {
  failureThreshold: number;    // Open after N failures (e.g., 5)
  recoveryTimeout: number;     // Try recovery after N ms (e.g., 60000)
  successThreshold: number;    // Close after N successes in HALF_OPEN (e.g., 2)
}

class CircuitBreaker {
  private state: CircuitState = CircuitState.CLOSED;
  private failureCount = 0;
  private successCount = 0;
  private lastFailureTime = 0;
  
  constructor(private config: CircuitBreakerConfig) {}
  
  async execute<T>(fn: () => Promise<T>): Promise<T> {
    // Check if circuit should transition to HALF_OPEN
    if (
      this.state === CircuitState.OPEN &&
      Date.now() - this.lastFailureTime > this.config.recoveryTimeout
    ) {
      console.log('Circuit transitioning to HALF_OPEN');
      this.state = CircuitState.HALF_OPEN;
      this.successCount = 0;
    }
    
    // Reject if circuit is OPEN
    if (this.state === CircuitState.OPEN) {
      throw new Error('Circuit breaker is OPEN');
    }
    
    try {
      const result = await fn();
      this.onSuccess();
      return result;
    } catch (error) {
      this.onFailure();
      throw error;
    }
  }
  
  private onSuccess(): void {
    this.failureCount = 0;
    
    if (this.state === CircuitState.HALF_OPEN) {
      this.successCount++;
      if (this.successCount >= this.config.successThreshold) {
        console.log('Circuit transitioning to CLOSED');
        this.state = CircuitState.CLOSED;
      }
    }
  }
  
  private onFailure(): void {
    this.lastFailureTime = Date.now();
    this.failureCount++;
    
    if (
      this.state === CircuitState.CLOSED &&
      this.failureCount >= this.config.failureThreshold
    ) {
      console.log('Circuit transitioning to OPEN');
      this.state = CircuitState.OPEN;
    } else if (this.state === CircuitState.HALF_OPEN) {
      console.log('Circuit returning to OPEN (recovery failed)');
      this.state = CircuitState.OPEN;
      this.successCount = 0;
    }
  }
  
  getState(): CircuitState {
    return this.state;
  }
}

// Usage
const langChainCircuit = new CircuitBreaker({
  failureThreshold: 5,
  recoveryTimeout: 60000, // 1 minute
  successThreshold: 2
});

export const aiRouter = onRequest({ ... }, async (req, res) => {
  try {
    const upstream = await langChainCircuit.execute(() =>
      forwardToLangChain(target.path, envelope, target.timeoutMs)
    );
    // ... handle response
  } catch (e: any) {
    if (e.message === 'Circuit breaker is OPEN') {
      res.status(503).json({
        error: 'Service temporarily unavailable',
        retryAfter: 60
      });
      return;
    }
    // ... other error handling
  }
});
```

---

### Option 2: Use Existing Library (Alternative)

**What:** Use `opossum` or similar circuit breaker library

**Pros:**
- Battle-tested implementation
- Rich features (metrics, events, etc.)
- Well-documented

**Cons:**
- Additional dependency
- More features than needed
- Potential bundle size increase

**Implementation:**

```typescript
import CircuitBreaker from 'opossum';

const breaker = new CircuitBreaker(forwardToLangChain, {
  timeout: 30000,
  errorThresholdPercentage: 50,
  resetTimeout: 60000
});

breaker.fallback(() => {
  return { status: 503, json: async () => ({ error: 'Service unavailable' }) };
});
```

---

### Option 3: Firestore-Backed Circuit Breaker (Overkill)

**What:** Share circuit state across function instances via Firestore

**Pros:**
- Global circuit state (all instances agree)
- Persistent across cold starts

**Cons:**
- Significant complexity
- Firestore latency (~30-50ms per check)
- Adds cost
- **Not recommended** for this use case

---

## Recommended Solution: Option 1 (Simple Implementation)

### Why Simple Implementation?

1. **In-memory is fine:** Each function instance can track its own failures
2. **No coordination needed:** If service is down, all instances will detect it
3. **Fast:** No external calls for circuit state
4. **Easy to test:** Deterministic behavior
5. **MVP-appropriate:** Solves the problem without over-engineering

### Implementation Steps

1. **Create circuit breaker module**
   ```bash
   # New file
   firebase-functions/functions/src/circuitBreaker.ts
   ```

2. **Add circuit breaker to aiRouter**
   ```typescript
   const langChainCircuit = new CircuitBreaker({
     failureThreshold: 5,
     recoveryTimeout: 60000,
     successThreshold: 2
   });
   ```

3. **Wrap forwardToLangChain calls**
   ```typescript
   const upstream = await langChainCircuit.execute(() =>
     forwardToLangChain(target.path, envelope, target.timeoutMs)
   );
   ```

4. **Add structured logging**
   ```typescript
   console.log(JSON.stringify({
     event: 'circuit_breaker_state_change',
     state: circuit.getState(),
     timestamp: Date.now()
   }));
   ```

5. **Add metrics endpoint (optional)**
   ```typescript
   export const circuitStatus = onRequest((req, res) => {
     res.json({ state: langChainCircuit.getState() });
   });
   ```

---

## Acceptance Criteria

- [ ] Circuit opens after 5 consecutive failures
- [ ] Circuit rejects requests while OPEN (fast fail)
- [ ] Circuit attempts recovery after 60s
- [ ] Circuit closes after 2 successful recovery attempts
- [ ] Proper HTTP 503 responses when circuit is OPEN
- [ ] Structured logging for state transitions
- [ ] Unit tests for all state transitions
- [ ] Integration test with simulated failures
- [ ] Documentation updated

---

## Testing Checklist

### Unit Tests
```typescript
describe('CircuitBreaker', () => {
  it('opens after threshold failures', async () => {
    const breaker = new CircuitBreaker({ failureThreshold: 3, ... });
    const fn = jest.fn().mockRejectedValue(new Error('fail'));
    
    // First 3 failures
    for (let i = 0; i < 3; i++) {
      await expect(breaker.execute(fn)).rejects.toThrow();
    }
    
    expect(breaker.getState()).toBe(CircuitState.OPEN);
  });
  
  it('transitions to HALF_OPEN after timeout', async () => {
    // ... test recovery timeout
  });
  
  it('closes after successful recovery', async () => {
    // ... test recovery success
  });
});
```

### Integration Test
```bash
# Simulate LangChain service failure
# Stop LangChain container
docker stop messageai-langchain

# Make 10 requests
for i in {1..10}; do
  curl -X POST $CF_URL/v1/template/generate \
    -H "Authorization: Bearer $TOKEN" \
    -d '{"requestId":"test","payload":{}}'
done

# Expected:
# - First 5 return 500 (failures)
# - Next 5 return 503 (circuit open)

# Wait 60s, restart service
docker start messageai-langchain
sleep 60

# Make 2 more requests
# Expected: Both succeed, circuit closes
```

---

## Configuration Recommendations

**Production Values:**
```typescript
{
  failureThreshold: 5,        // Open after 5 failures
  recoveryTimeout: 60000,     // Try recovery after 1 minute
  successThreshold: 2         // Close after 2 successes
}
```

**Aggressive (High Traffic):**
```typescript
{
  failureThreshold: 10,       // More tolerance
  recoveryTimeout: 30000,     // Faster recovery attempts
  successThreshold: 3         // More confidence before closing
}
```

**Conservative (Low Traffic):**
```typescript
{
  failureThreshold: 3,        // Less tolerance
  recoveryTimeout: 120000,    // Slower recovery
  successThreshold: 1         // Quick close on success
}
```

---

## Related Files

### Files to Create
- `firebase-functions/functions/src/circuitBreaker.ts` - Circuit breaker implementation
- `firebase-functions/functions/test/circuitBreaker.test.ts` - Unit tests

### Files to Modify
- `firebase-functions/functions/src/index.ts` - Integrate circuit breaker
- `firebase-functions/functions/package.json` - Update if using library

### Related Documentation
- QC Report: `docs/reviews/BLOCKS_B_B2_QC_REPORT.md`
- Sprint 2 Task Plan: `docs/product/messageai-sprint2-task-plan.md`

---

## Additional Notes

### Error Classification

Not all errors should trip the circuit:
```typescript
function shouldTripCircuit(error: any): boolean {
  // Trip on:
  // - Network errors (ECONNREFUSED, ETIMEDOUT)
  // - 5xx server errors
  // - Timeouts
  
  // Don't trip on:
  // - 4xx client errors (bad request, auth, etc.)
  // - AbortController timeouts (expected behavior)
  
  return error.code === 'ECONNREFUSED' ||
         error.code === 'ETIMEDOUT' ||
         (error.status && error.status >= 500);
}
```

### Monitoring

Add Cloud Monitoring metrics:
```typescript
// Custom metric for circuit state
import { Monitoring } from '@google-cloud/monitoring';

const monitoring = new Monitoring.MetricServiceClient();
await monitoring.createTimeSeries({
  name: monitoring.projectPath(projectId),
  timeSeries: [{
    metric: { type: 'custom.googleapis.com/circuit_breaker/state' },
    points: [{ value: { int64Value: stateToInt(circuit.getState()) } }]
  }]
});
```

---

## Success Metrics

✅ **Definition of Done:**
1. Circuit breaker prevents cascade failures (verified by test)
2. Fast fail when circuit is OPEN (<100ms response)
3. Automatic recovery when service returns
4. All unit tests pass
5. Integration test simulates failure/recovery
6. Structured logging for observability
7. Clean git commit with descriptive message

---

## References

- [Circuit Breaker Pattern](https://martinfowler.com/bliki/CircuitBreaker.html)
- [Release It! Book](https://pragprog.com/titles/mnee2/release-it-second-edition/) - Chapter on Circuit Breakers
- [AWS Circuit Breaker](https://aws.amazon.com/builders-library/using-load-shedding-to-avoid-overload/)
- [Opossum Library](https://nodeshift.dev/opossum/)

---

**Created by:** QC Agent (Blocks B & B2 Review)  
**Related Sprint:** Sprint 2 - AI Integration (Post-MVP Hardening)  
**Blocks:** None (enhancement)  
**Ticket ID:** FIREBASE-FUNCTIONS-002

