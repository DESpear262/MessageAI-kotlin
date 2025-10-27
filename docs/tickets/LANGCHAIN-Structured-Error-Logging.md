# TICKET: Add Structured Error Logging to LangChain Service

**Status:** 🟡 Backlog  
**Priority:** Low  
**Type:** Observability / Maintenance  
**Estimated Effort:** 2-3 hours  
**Assignee:** TBD  
**Created:** 2025-10-24

---

## Problem Summary

The LangChain service has silent error handling that makes debugging difficult:

```python
# langchain-service/app/rag.py lines 36-39
try:
    self._embeds[mid] = self.llm.embed(text)
except Exception:
    # Silent fallback - no logging
    self._embeds[mid] = []
```

**Current Issues:**
- Errors swallowed without logging
- No context about what failed or why
- Difficult to debug production issues
- No visibility into failure patterns

**Impact:**
- Low: Service degrades gracefully but silently
- Debugging: Hard to diagnose embedding failures
- Monitoring: No metrics on error rates
- User Experience: Reduced quality responses (empty embeddings)

---

## Root Cause Analysis

### Technical Details

**Current Error Handling Patterns:**

1. **Silent fallbacks in RAG:**
```python
except Exception:
    self._embeds[mid] = []  # What went wrong? Why? When?
```

2. **Generic error responses:**
```python
def _err(request_id: str, message: str, status: int = 500):
    return JSONResponse(
        status_code=status,
        content=AiResponseEnvelope(..., error=message).model_dump()
    )
    # No structured logging, no context
```

3. **No error classification:**
- All errors treated equally
- No distinction between transient vs permanent
- No retry strategies

**Why This Matters:**

**Production Scenario:**
```
Timeline:
10:00 - OpenAI API key expires
10:01 - First request fails, returns empty embedding (silent)
10:05 - 100 requests failed, all with degraded responses
10:30 - User reports "AI not working properly"
11:00 - Engineer checks logs, finds nothing
11:30 - Finally discovers expired key via trial-and-error
```

**With Structured Logging:**
```
Timeline:
10:00 - OpenAI API key expires
10:01 - First request logs: {"error": "openai_api_error", "status": 401, "message": "Unauthorized"}
10:02 - Alert fires: "High rate of OpenAI 401 errors"
10:03 - Engineer rotates API key
10:05 - Service recovers
```

---

## Solution Options

### Option 1: Structured Logging with Python logging (Recommended)

**What:** Use Python's `logging` module with JSON formatter

**Pros:**
- Standard library (no dependencies)
- Integrates with Cloud Logging
- Structured for easy querying
- Configurable log levels

**Cons:**
- Requires setup/configuration
- Need JSON formatter

**Implementation:**

```python
import logging
import json
import sys
from datetime import datetime

# Configure structured logging
class JSONFormatter(logging.Formatter):
    def format(self, record):
        log_obj = {
            'timestamp': datetime.utcnow().isoformat(),
            'level': record.levelname,
            'message': record.getMessage(),
            'module': record.module,
            'function': record.funcName,
            'line': record.lineno
        }
        
        # Add extra fields
        if hasattr(record, 'request_id'):
            log_obj['request_id'] = record.request_id
        if hasattr(record, 'error_type'):
            log_obj['error_type'] = record.error_type
        if hasattr(record, 'context'):
            log_obj['context'] = record.context
        
        return json.dumps(log_obj)

# Setup
logger = logging.getLogger('messageai')
handler = logging.StreamHandler(sys.stdout)
handler.setFormatter(JSONFormatter())
logger.addHandler(handler)
logger.setLevel(logging.INFO)
```

**Usage in RAG:**
```python
# app/rag.py
import logging
logger = logging.getLogger('messageai.rag')

class RAGCache:
    def index_messages(self, messages: List[Dict[str, Any]], max_items: int = 300):
        for m in messages[:max_items]:
            mid = m.get("id")
            text = str(m.get("text") or "").strip()
            # ... validation ...
            
            try:
                self._embeds[mid] = self.llm.embed(text)
            except Exception as e:
                # Structured error logging
                logger.error(
                    "Failed to generate embedding",
                    extra={
                        'error_type': type(e).__name__,
                        'message_id': mid,
                        'error_message': str(e),
                        'text_length': len(text)
                    }
                )
                # Fallback
                self._embeds[mid] = []
```

**Output:**
```json
{
  "timestamp": "2025-10-24T10:01:23.456Z",
  "level": "ERROR",
  "message": "Failed to generate embedding",
  "module": "rag",
  "function": "index_messages",
  "error_type": "OpenAIError",
  "message_id": "msg_12345",
  "error_message": "Authentication failed: Invalid API key",
  "text_length": 128
}
```

---

### Option 2: OpenTelemetry (Production-Grade)

**What:** Use OpenTelemetry for logs, traces, and metrics

**Pros:**
- Industry standard
- Rich ecosystem
- Unified observability
- Auto-instrumentation

**Cons:**
- More complex setup
- Additional dependencies
- Overkill for MVP

**Implementation:**

```python
from opentelemetry import trace
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor
from opentelemetry.exporter.cloud_trace import CloudTraceSpanExporter

# Setup
trace.set_tracer_provider(TracerProvider())
tracer = trace.get_tracer(__name__)
span_processor = BatchSpanProcessor(CloudTraceSpanExporter())
trace.get_tracer_provider().add_span_processor(span_processor)

# Usage
with tracer.start_as_current_span("embed_message") as span:
    try:
        embedding = self.llm.embed(text)
        span.set_attribute("success", True)
    except Exception as e:
        span.set_attribute("error", True)
        span.set_attribute("error.type", type(e).__name__)
        span.set_attribute("error.message", str(e))
        raise
```

---

### Option 3: Simple Print Statements with Structure (Quick Fix)

**What:** Add structured JSON prints for errors

**Pros:**
- Zero setup
- Works immediately
- Cloud Logging auto-parses JSON

**Cons:**
- Not configurable
- Hard to filter/control
- Less features than proper logging

**Implementation:**

```python
import json
import time

def log_error(message: str, **kwargs):
    print(json.dumps({
        'timestamp': time.time(),
        'level': 'ERROR',
        'message': message,
        **kwargs
    }), file=sys.stderr)

# Usage
try:
    self._embeds[mid] = self.llm.embed(text)
except Exception as e:
    log_error(
        "Embedding generation failed",
        error_type=type(e).__name__,
        error_message=str(e),
        message_id=mid,
        text_length=len(text)
    )
    self._embeds[mid] = []
```

---

## Recommended Solution: Option 1 (Python logging)

### Why Python logging?

1. **Standard:** Pythonic way to log
2. **Flexible:** Easy to configure per environment
3. **Compatible:** Works with Cloud Logging, Datadog, etc.
4. **Lightweight:** Minimal overhead
5. **Testable:** Can capture logs in tests

### Implementation Steps

1. **Create logging config**
   ```python
   # app/logging_config.py
   import logging
   import sys
   from .json_formatter import JSONFormatter
   
   def setup_logging(level: str = "INFO"):
       logger = logging.getLogger('messageai')
       logger.setLevel(getattr(logging, level))
       
       handler = logging.StreamHandler(sys.stdout)
       handler.setFormatter(JSONFormatter())
       logger.addHandler(handler)
       
       return logger
   ```

2. **Initialize in main.py**
   ```python
   from .logging_config import setup_logging
   from .config import LOG_LEVEL
   
   setup_logging(LOG_LEVEL)
   logger = logging.getLogger('messageai')
   ```

3. **Update error handling throughout**
   ```python
   # app/rag.py
   logger = logging.getLogger('messageai.rag')
   
   # app/providers.py
   logger = logging.getLogger('messageai.providers')
   
   # app/firestore_client.py
   logger = logging.getLogger('messageai.firestore')
   ```

4. **Add request logging middleware**
   ```python
   @app.middleware("http")
   async def log_requests(request: Request, call_next):
       request_id = request.headers.get("x-request-id", "unknown")
       logger.info(
           "Request received",
           extra={
               'request_id': request_id,
               'path': request.url.path,
               'method': request.method
           }
       )
       
       start = time.time()
       response = await call_next(request)
       duration = time.time() - start
       
       logger.info(
           "Request completed",
           extra={
               'request_id': request_id,
               'status_code': response.status_code,
               'duration_ms': int(duration * 1000)
           }
       )
       
       return response
   ```

---

## Acceptance Criteria

- [ ] Structured JSON logging configured
- [ ] All error cases log with context
- [ ] Request/response logging added
- [ ] Log levels configurable via environment
- [ ] Errors classified by type
- [ ] No more silent failures
- [ ] Integration with Cloud Logging
- [ ] Unit tests for logging behavior
- [ ] Documentation updated

---

## Error Classification

### Categories to Log

1. **OpenAI API Errors:**
   ```python
   logger.error(
       "OpenAI API error",
       extra={
           'error_type': 'openai_api_error',
           'status_code': e.status_code,
           'message': str(e),
           'model': model_name
       }
   )
   ```

2. **Firestore Errors:**
   ```python
   logger.error(
       "Firestore query failed",
       extra={
           'error_type': 'firestore_error',
           'collection': 'messages',
           'chat_id': chat_id,
           'message': str(e)
       }
   )
   ```

3. **Validation Errors:**
   ```python
   logger.warning(
       "Invalid request payload",
       extra={
           'error_type': 'validation_error',
           'request_id': request_id,
           'field': field_name,
           'message': error_message
       }
   )
   ```

4. **Business Logic Errors:**
   ```python
   logger.info(
       "No recent messages found",
       extra={
           'error_type': 'no_data',
           'chat_id': chat_id,
           'time_window': time_window
       }
   )
   ```

---

## Testing Checklist

### Unit Tests

```python
# test_logging.py
import logging
from testfixtures import LogCapture

def test_embedding_failure_logged():
    with LogCapture() as logs:
        # Trigger embedding failure
        rag = RAGCache(mock_failing_llm)
        rag.index_messages([{"id": "test", "text": "hello"}])
        
        # Verify log
        logs.check_present(
            ('messageai.rag', 'ERROR', 'Failed to generate embedding')
        )

def test_request_logging():
    with LogCapture() as logs:
        client = TestClient(app)
        response = client.post("/template/generate", json={...})
        
        # Verify request/response logged
        assert 'Request received' in str(logs)
        assert 'Request completed' in str(logs)
```

### Manual Testing

```bash
# 1. Start service with DEBUG logging
export LOG_LEVEL=DEBUG
uvicorn app.main:app --reload

# 2. Make request with invalid OpenAI key
export OPENAI_API_KEY=invalid
# Expected: Detailed error logs in JSON format

# 3. Check Cloud Logging (if deployed)
gcloud logging read "resource.type=cloud_run_revision" --limit 50
# Expected: Structured logs with context
```

---

## Query Examples (Cloud Logging)

```sql
-- All errors in last hour
resource.type="cloud_run_revision"
AND jsonPayload.level="ERROR"
AND timestamp > "2025-10-24T10:00:00Z"

-- OpenAI API errors
jsonPayload.error_type="openai_api_error"

-- Slow requests (>5s)
jsonPayload.duration_ms > 5000

-- Errors for specific request
jsonPayload.request_id="abc-123-def"
```

---

## Related Files

### Files to Create
- `langchain-service/app/logging_config.py` - Logging setup
- `langchain-service/app/json_formatter.py` - JSON formatter
- `langchain-service/test/test_logging.py` - Unit tests

### Files to Modify
- `langchain-service/app/main.py` - Initialize logging, add middleware
- `langchain-service/app/rag.py` - Add structured error logs
- `langchain-service/app/providers.py` - Add structured error logs
- `langchain-service/app/firestore_client.py` - Add structured error logs
- `langchain-service/app/config.py` - Add LOG_LEVEL
- `langchain-service/README.md` - Document logging

### Related Documentation
- QC Report: `docs/reviews/BLOCKS_B_B2_QC_REPORT.md`

---

## Additional Notes

### Log Levels Guide

```python
# ERROR: Something failed that shouldn't
logger.error("OpenAI API call failed", ...)

# WARNING: Something unexpected but handled
logger.warning("Empty message text, skipping", ...)

# INFO: Normal operation events
logger.info("Request completed", ...)

# DEBUG: Detailed information for debugging
logger.debug("Embedding generated", extra={'dimensions': len(vec)})
```

### Performance Impact

- JSON formatting: ~0.1-0.5ms per log
- I/O (stdout): Async, negligible
- **Total overhead: <1ms per request**

### Environment Configuration

```bash
# Development
LOG_LEVEL=DEBUG

# Production
LOG_LEVEL=INFO

# Troubleshooting
LOG_LEVEL=DEBUG  # Temporarily
```

---

## Success Metrics

✅ **Definition of Done:**
1. All exceptions logged with context
2. No more silent failures
3. Request/response logging active
4. Log levels configurable
5. Structured JSON format
6. Cloud Logging integration works
7. Query examples documented
8. Clean git commit with descriptive message

---

## Future Enhancements

Once basic logging is in place:
- Add metrics (Prometheus/OpenTelemetry)
- Add distributed tracing
- Add error rate alerting
- Add log sampling for high-volume endpoints
- Add PII redaction for sensitive data

---

## References

- [Python Logging HOWTO](https://docs.python.org/3/howto/logging.html)
- [Google Cloud Logging](https://cloud.google.com/logging/docs/reference/libraries#client-libraries-usage-python)
- [Structured Logging Best Practices](https://www.structlog.org/en/stable/why.html)
- [FastAPI Logging](https://fastapi.tiangolo.com/advanced/middleware/#middleware)

---

**Created by:** QC Agent (Blocks B & B2 Review)  
**Related Sprint:** Sprint 2 - AI Integration (Post-MVP Hardening)  
**Blocks:** None (observability enhancement)  
**Ticket ID:** LANGCHAIN-SERVICE-003

