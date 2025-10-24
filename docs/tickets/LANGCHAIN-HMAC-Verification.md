# TICKET: Add HMAC Signature Verification to LangChain Service

**Status:** 🟡 Backlog  
**Priority:** Low (Defense in Depth)  
**Type:** Security Enhancement  
**Estimated Effort:** 2-3 hours  
**Assignee:** TBD  
**Created:** 2025-10-24

---

## Problem Summary

The LangChain service trusts that the Firebase Function proxy has already validated requests:

```python
# langchain-service/app/main.py
@app.post("/template/generate")
def generate_template(body: AiRequestEnvelope):
    # No signature verification
    # Assumes proxy handled auth
    request_id = body.requestId
    # ... process request
```

**Current Security Model:**
```
Android → Firebase Auth → CF Proxy (HMAC signing) → LangChain (trusts proxy)
                            ↓ validates
                      Network isolation
```

**Risk:**
- If network isolation fails, LangChain could receive unauthorized requests
- No defense-in-depth
- Compliance/audit findings (no signature verification)

**Impact:**
- Low: Acceptable for MVP if deployed in private network
- Best Practice: Defense in depth is security standard
- Audit: May be flagged in security review

---

## Root Cause Analysis

### Technical Details

**Current Architecture:**
- Firebase CF proxy signs requests with HMAC-SHA256
- LangChain service doesn't verify signatures
- Security relies solely on network isolation

**What CF Sends:**
```typescript
// firebase-functions/functions/src/index.ts lines 337-343
headers: {
  'Content-Type': 'application/json',
  'x-request-id': envelope.requestId,
  'x-uid': String(envelope.context['uid'] ?? ''),
  'x-sig': sig,           // HMAC signature
  'x-sig-ts': ts,         // Timestamp
}
```

**Signature Generation (CF):**
```typescript
const payloadHash = crypto.createHash('sha256').update(JSON.stringify(envelope.payload)).digest('hex');
const base = `${envelope.requestId}.${ts}.${payloadHash}`;
const sig = crypto.createHmac('sha256', secret).update(base).digest('hex');
```

**Why Verification Matters (Defense in Depth):**
1. **Compromise of proxy:** If CF is compromised, LangChain still protected
2. **Misconfiguration:** If network isolation fails, unauthorized access prevented
3. **Compliance:** Many frameworks require signature verification
4. **Audit trail:** Logs invalid signature attempts

---

## Solution Options

### Option 1: FastAPI Middleware (Recommended)

**What:** Add middleware to verify signatures on all requests

**Pros:**
- Centralized verification (DRY)
- Applies to all endpoints automatically
- Easy to enable/disable via config
- Standard FastAPI pattern

**Cons:**
- Slight latency overhead (~5-10ms)
- Requires shared secret management

**Implementation:**

```python
import hmac
import hashlib
import time
from fastapi import Request, HTTPException
from starlette.middleware.base import BaseHTTPMiddleware

class HMACVerificationMiddleware(BaseHTTPMiddleware):
    def __init__(self, app, shared_secret: str, max_age_seconds: int = 300):
        super().__init__(app)
        self.shared_secret = shared_secret
        self.max_age_seconds = max_age_seconds
    
    async def dispatch(self, request: Request, call_next):
        # Skip verification for health check
        if request.url.path == "/healthz":
            return await call_next(request)
        
        # Extract signature headers
        sig = request.headers.get("x-sig")
        ts = request.headers.get("x-sig-ts")
        request_id = request.headers.get("x-request-id")
        
        if not sig or not ts or not request_id:
            raise HTTPException(
                status_code=401,
                detail="Missing signature headers"
            )
        
        # Check timestamp (prevent replay attacks)
        try:
            sig_time = int(ts)
            now = int(time.time() * 1000)
            age_seconds = (now - sig_time) / 1000
            
            if age_seconds > self.max_age_seconds:
                raise HTTPException(
                    status_code=401,
                    detail="Signature expired"
                )
        except ValueError:
            raise HTTPException(
                status_code=401,
                detail="Invalid timestamp"
            )
        
        # Read and verify body
        body = await request.body()
        payload_hash = hashlib.sha256(body).hexdigest()
        
        # Reconstruct signature base
        base = f"{request_id}.{ts}.{payload_hash}"
        expected_sig = hmac.new(
            self.shared_secret.encode(),
            base.encode(),
            hashlib.sha256
        ).hexdigest()
        
        # Constant-time comparison
        if not hmac.compare_digest(sig, expected_sig):
            # Log for security monitoring
            print(json.dumps({
                'event': 'invalid_signature',
                'request_id': request_id,
                'timestamp': now,
                'ip': request.client.host
            }))
            raise HTTPException(
                status_code=401,
                detail="Invalid signature"
            )
        
        # Signature valid, proceed
        response = await call_next(request)
        return response
```

**Usage:**
```python
# In app/main.py
from .middleware import HMACVerificationMiddleware
from .config import LANGCHAIN_SHARED_SECRET

app = FastAPI(...)

# Add middleware if secret is configured
if LANGCHAIN_SHARED_SECRET:
    app.add_middleware(
        HMACVerificationMiddleware,
        shared_secret=LANGCHAIN_SHARED_SECRET,
        max_age_seconds=300  # 5 minutes
    )
```

---

### Option 2: Dependency Injection (Alternative)

**What:** Use FastAPI dependencies for verification

**Pros:**
- More granular control (per-endpoint)
- Explicit in endpoint signatures
- Easy to test

**Cons:**
- Repetitive (must add to each endpoint)
- Easy to forget on new endpoints

**Implementation:**

```python
from fastapi import Depends, Header, HTTPException

async def verify_signature(
    x_sig: str = Header(...),
    x_sig_ts: str = Header(...),
    x_request_id: str = Header(...),
    request: Request
):
    # Same verification logic as middleware
    # Raise HTTPException if invalid
    pass

@app.post("/template/generate")
def generate_template(
    body: AiRequestEnvelope,
    verified: None = Depends(verify_signature)
):
    # Signature already verified by dependency
    # ... process request
```

---

### Option 3: Decorator Pattern (Alternative)

**What:** Create decorator for endpoints requiring verification

**Pros:**
- Clean syntax
- Opt-in per endpoint
- Pythonic

**Cons:**
- Still repetitive
- Less idiomatic for FastAPI

**Implementation:**

```python
def require_hmac_signature(func):
    @wraps(func)
    async def wrapper(body: AiRequestEnvelope, request: Request):
        verify_signature_from_request(request, body)
        return await func(body)
    return wrapper

@app.post("/template/generate")
@require_hmac_signature
def generate_template(body: AiRequestEnvelope):
    # ... process request
```

---

## Recommended Solution: Option 1 (Middleware)

### Why Middleware?

1. **Centralized:** Single point of verification
2. **Automatic:** Applies to all endpoints (can't forget)
3. **Standard:** FastAPI best practice for cross-cutting concerns
4. **Testable:** Easy to unit test middleware in isolation
5. **Configurable:** Enable/disable via environment variable

### Implementation Steps

1. **Create middleware module**
   ```bash
   langchain-service/app/middleware.py
   ```

2. **Add shared secret to config**
   ```python
   # app/config.py
   LANGCHAIN_SHARED_SECRET = os.getenv("LANGCHAIN_SHARED_SECRET", "")
   ```

3. **Add middleware to app**
   ```python
   # app/main.py
   if LANGCHAIN_SHARED_SECRET:
       app.add_middleware(HMACVerificationMiddleware, ...)
   ```

4. **Update environment variables**
   ```bash
   # .env or deployment config
   LANGCHAIN_SHARED_SECRET=same-secret-as-firebase-function
   ```

5. **Add logging for security events**
   ```python
   # Invalid signatures logged for monitoring
   print(json.dumps({
       'event': 'invalid_signature',
       'severity': 'WARNING',
       'request_id': request_id,
       'timestamp': time.time()
   }))
   ```

---

## Acceptance Criteria

- [ ] Middleware verifies HMAC signatures
- [ ] Invalid signatures return 401 Unauthorized
- [ ] Expired signatures (>5min) rejected
- [ ] Health check endpoint bypasses verification
- [ ] Constant-time comparison prevents timing attacks
- [ ] Security events logged for monitoring
- [ ] Unit tests for all failure cases
- [ ] Integration test with CF proxy
- [ ] Performance impact < 10ms per request
- [ ] Documentation updated

---

## Testing Checklist

### Unit Tests

```python
# test_middleware.py
from fastapi.testclient import TestClient
import hmac
import hashlib
import time

def test_valid_signature():
    client = TestClient(app)
    
    # Generate valid signature
    request_id = "test-123"
    ts = str(int(time.time() * 1000))
    payload = {"requestId": request_id, "payload": {}}
    payload_hash = hashlib.sha256(json.dumps(payload).encode()).hexdigest()
    base = f"{request_id}.{ts}.{payload_hash}"
    sig = hmac.new(SECRET.encode(), base.encode(), hashlib.sha256).hexdigest()
    
    response = client.post(
        "/template/generate",
        json=payload,
        headers={
            "x-request-id": request_id,
            "x-sig": sig,
            "x-sig-ts": ts
        }
    )
    
    assert response.status_code == 200

def test_invalid_signature():
    client = TestClient(app)
    response = client.post(
        "/template/generate",
        json={"requestId": "test"},
        headers={
            "x-request-id": "test",
            "x-sig": "invalid",
            "x-sig-ts": str(int(time.time() * 1000))
        }
    )
    assert response.status_code == 401

def test_expired_signature():
    # Test with timestamp > 5 minutes old
    old_ts = str(int((time.time() - 400) * 1000))
    # ... generate signature with old_ts
    # assert 401
```

### Integration Test

```bash
# Test with actual CF proxy
# Make request through CF → LangChain
curl -X POST $CF_URL/v1/template/generate \
  -H "Authorization: Bearer $FIREBASE_TOKEN" \
  -d '{"requestId":"test","payload":{}}'

# Expected: 200 OK (CF signs, LangChain verifies)

# Test direct to LangChain (bypassing CF)
curl -X POST $LANGCHAIN_URL/template/generate \
  -d '{"requestId":"test","payload":{}}'

# Expected: 401 Unauthorized (no signature)
```

---

## Configuration

**Environment Variables:**
```bash
# Required for verification
LANGCHAIN_SHARED_SECRET=<same-as-firebase-function-secret>

# Optional tuning
SIGNATURE_MAX_AGE_SECONDS=300  # Default 5 minutes
SIGNATURE_VERIFICATION_ENABLED=true  # Default true if secret present
```

**Deployment:**
```bash
# Cloud Run
gcloud run deploy messageai-langchain \
  --set-secrets LANGCHAIN_SHARED_SECRET=langchain-secret:latest \
  --set-env-vars SIGNATURE_VERIFICATION_ENABLED=true

# Docker
docker run \
  -e LANGCHAIN_SHARED_SECRET=xxx \
  messageai-langchain:latest
```

---

## Security Considerations

### Timing Attack Prevention

Use `hmac.compare_digest()` for constant-time comparison:
```python
# BAD: Timing attack vulnerable
if sig == expected_sig:
    pass

# GOOD: Constant-time comparison
if hmac.compare_digest(sig, expected_sig):
    pass
```

### Replay Attack Prevention

- Timestamp included in signature
- Max age enforced (5 minutes default)
- Each request has unique `request_id`

### Secret Rotation

To rotate shared secret:
1. Add new secret to both CF and LangChain
2. Update CF to send both old and new signature
3. LangChain accepts either signature (grace period)
4. After 24h, remove old secret

---

## Related Files

### Files to Create
- `langchain-service/app/middleware.py` - HMAC verification middleware
- `langchain-service/test/test_middleware.py` - Unit tests

### Files to Modify
- `langchain-service/app/main.py` - Add middleware
- `langchain-service/app/config.py` - Add LANGCHAIN_SHARED_SECRET
- `langchain-service/README.md` - Document verification

### Related Documentation
- QC Report: `docs/reviews/BLOCKS_B_B2_QC_REPORT.md`
- Firebase Functions: `firebase-functions/functions/src/index.ts` (signing logic)

---

## Additional Notes

### When to Skip Verification

```python
# Bypass for specific paths
SKIP_VERIFICATION_PATHS = ["/healthz", "/metrics", "/docs"]

if request.url.path in SKIP_VERIFICATION_PATHS:
    return await call_next(request)
```

### Monitoring & Alerting

Set up alerts for:
- High rate of invalid signatures (potential attack)
- Signatures with very old timestamps (clock skew?)
- Repeated failures from same IP

```python
# Log structured events
import logging
logger = logging.getLogger("security")

logger.warning({
    "event": "signature_verification_failed",
    "reason": "expired",
    "request_id": request_id,
    "age_seconds": age_seconds,
    "client_ip": request.client.host
})
```

---

## Success Metrics

✅ **Definition of Done:**
1. Middleware verifies all requests (except health check)
2. Invalid signatures rejected with 401
3. All unit tests pass
4. Integration test with CF proxy passes
5. Performance impact < 10ms
6. Security events logged
7. Clean git commit with descriptive message

---

## Performance Impact

**Overhead Analysis:**
- HMAC SHA256 computation: ~1-2ms
- JSON parsing (body): ~1-3ms
- Timestamp validation: <1ms
- Total: ~5-10ms per request

**Context:**
- Total AI request time: 2-5 seconds
- HMAC overhead: <0.5% of total latency
- **Acceptable trade-off for security**

---

## References

- [HMAC Wikipedia](https://en.wikipedia.org/wiki/HMAC)
- [FastAPI Middleware](https://fastapi.tiangolo.com/tutorial/middleware/)
- [Timing Attack Prevention](https://codahale.com/a-lesson-in-timing-attacks/)
- [API Security Best Practices](https://owasp.org/www-project-api-security/)

---

**Created by:** QC Agent (Blocks B & B2 Review)  
**Related Sprint:** Sprint 2 - AI Integration (Post-MVP Hardening)  
**Blocks:** None (security enhancement)  
**Ticket ID:** LANGCHAIN-SERVICE-002

