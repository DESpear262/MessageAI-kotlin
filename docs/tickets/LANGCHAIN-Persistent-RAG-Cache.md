# TICKET: Persistent RAG Cache for LangChain Service

**Status:** 🟡 Backlog  
**Priority:** Medium  
**Type:** Enhancement / Performance  
**Estimated Effort:** 6-8 hours  
**Assignee:** TBD  
**Created:** 2025-10-24

---

## Problem Summary

The LangChain service uses in-memory RAG cache that is ephemeral:

```python
# langchain-service/app/rag.py lines 20-40
class RAGCache:
    def __init__(self, llm: OpenAIProvider) -> None:
        self.llm = llm
        self._embeds: Dict[str, List[float]] = {}  # In-memory only
        self._texts: Dict[str, str] = {}
```

**Current Limitations:**
- Cold starts lose all indexed messages
- Every request re-indexes messages from Firestore
- No sharing of embeddings across requests/instances
- Repeated embedding calls to OpenAI (costs ~$0.020 per 1M tokens)

**Impact:**
- Medium: Acceptable for MVP but inefficient
- Cost: Redundant OpenAI embedding API calls
- Performance: Slower first-request after cold start
- Scale: Re-indexing overhead increases with message volume

---

## Root Cause Analysis

### Technical Details

**Current Flow:**
```
Request → Fetch messages from Firestore → Generate embeddings → Index in memory → Query → Response
                                              ↓ (OpenAI API)
                                          $0.020 per 1M tokens
```

**Problem:**
- Same message embedded multiple times across cold starts
- No cache hits between function instances
- Embedding generation is slowest part of RAG (~200-500ms per batch)

**Example Cost Analysis:**
```
Assumptions:
- 1000 AI requests/day
- Average 50 messages per context
- 100 tokens per message (average)

Current (no cache):
- 1000 requests × 50 messages × 100 tokens = 5M tokens/day
- Cost: $0.10/day = $3/month for embeddings

With persistent cache (90% hit rate):
- Only 500K new tokens/day need embedding
- Cost: $0.01/day = $0.30/month
- Savings: $2.70/month (90% reduction)
```

---

## Solution Options

### Option 1: Redis Cache (Recommended for Production)

**What:** Use Redis/Memorystore for fast vector storage

**Pros:**
- Ultra-fast (<5ms lookup)
- Persistent across cold starts
- Shared across all instances
- Built-in TTL for auto-cleanup
- RedisSearch module supports vector similarity

**Cons:**
- Infrastructure cost (~$50/month Memorystore basic)
- Requires VPC networking
- More complex setup

**Implementation:**

```python
import redis
import numpy as np
from redis.commands.search.query import Query

class RedisCachedRAG:
    def __init__(self, llm: OpenAIProvider):
        self.llm = llm
        self.redis = redis.Redis(
            host=os.getenv('REDIS_HOST'),
            port=6379,
            decode_responses=False
        )
    
    def index_messages(self, messages: List[Dict[str, Any]], max_items: int = 300):
        pipe = self.redis.pipeline()
        for m in messages[:max_items]:
            mid = m.get("id")
            text = str(m.get("text") or "").strip()
            if not text:
                continue
            
            # Check if already cached
            cache_key = f"embed:{mid}"
            if self.redis.exists(cache_key):
                continue
            
            # Generate and cache embedding
            try:
                embedding = self.llm.embed(text)
                # Store as bytes for efficiency
                pipe.set(cache_key, np.array(embedding).tobytes())
                pipe.expire(cache_key, 7 * 24 * 3600)  # 7 day TTL
                
                # Also store text for retrieval
                pipe.set(f"text:{mid}", text)
                pipe.expire(f"text:{mid}", 7 * 24 * 3600)
            except Exception as e:
                print(f"Error embedding message {mid}: {e}")
        
        pipe.execute()
    
    def top_k(self, query: str, k: int = 20) -> List[Tuple[str, str, float]]:
        # Generate query embedding
        qv = self.llm.embed(query)
        
        # Get all cached embeddings
        keys = self.redis.keys("embed:*")
        scored = []
        
        for key in keys:
            mid = key.decode().split(":")[1]
            embed_bytes = self.redis.get(key)
            if not embed_bytes:
                continue
            
            vec = np.frombuffer(embed_bytes, dtype=np.float64)
            score = self._cosine(qv, vec.tolist())
            text = (self.redis.get(f"text:{mid}") or b"").decode()
            scored.append((mid, text, score))
        
        scored.sort(key=lambda t: t[2], reverse=True)
        return scored[:k]
```

**Cost:** ~$50/month for Memorystore, saves ~$3/month in embeddings

---

### Option 2: Firestore Cache (Cost-Effective)

**What:** Store embeddings in Firestore collection

**Pros:**
- No new infrastructure
- Persistent and shared
- Already using Firestore
- Low cost for read/write

**Cons:**
- Slower than Redis (~30-50ms vs <5ms)
- No native vector search
- Higher Firestore costs for large datasets

**Implementation:**

```python
from google.cloud import firestore

class FirestoreCachedRAG:
    def __init__(self, llm: OpenAIProvider):
        self.llm = llm
        self.db = firestore.Client()
        self._local_cache = {}  # L1 cache
    
    def index_messages(self, messages: List[Dict[str, Any]], max_items: int = 300):
        batch = self.db.batch()
        for m in messages[:max_items]:
            mid = m.get("id")
            text = str(m.get("text") or "").strip()
            if not text:
                continue
            
            # Check L1 cache first
            if mid in self._local_cache:
                continue
            
            # Check Firestore
            doc_ref = self.db.collection('embeddings').document(mid)
            doc = doc_ref.get()
            
            if doc.exists:
                # Load from Firestore into L1 cache
                data = doc.to_dict()
                self._local_cache[mid] = {
                    'text': data['text'],
                    'embedding': data['embedding']
                }
                continue
            
            # Generate embedding and cache
            try:
                embedding = self.llm.embed(text)
                self._local_cache[mid] = {'text': text, 'embedding': embedding}
                
                # Write to Firestore
                batch.set(doc_ref, {
                    'text': text,
                    'embedding': embedding,
                    'createdAt': firestore.SERVER_TIMESTAMP
                })
            except Exception as e:
                print(f"Error embedding message {mid}: {e}")
        
        batch.commit()
    
    def top_k(self, query: str, k: int = 20) -> List[Tuple[str, str, float]]:
        qv = self.llm.embed(query)
        scored = []
        
        for mid, data in self._local_cache.items():
            score = self._cosine(qv, data['embedding'])
            scored.append((mid, data['text'], score))
        
        scored.sort(key=lambda t: t[2], reverse=True)
        return scored[:k]
```

**Cost:** ~$0.36 per 1M AI requests (read+write), saves ~$3/month in embeddings

---

### Option 3: Pinecone/Weaviate (Production-Grade Vector DB)

**What:** Use dedicated vector database

**Pros:**
- Built for vector similarity search
- Blazing fast (milliseconds)
- Scales to billions of vectors
- Advanced features (metadata filtering, etc.)

**Cons:**
- Third-party service (~$70/month minimum)
- Another vendor to manage
- Overkill for MVP scale

**Implementation:**

```python
import pinecone

pinecone.init(api_key=os.getenv('PINECONE_API_KEY'))
index = pinecone.Index('messageai-rag')

class PineconeCachedRAG:
    def index_messages(self, messages: List[Dict[str, Any]], max_items: int = 300):
        vectors = []
        for m in messages[:max_items]:
            mid = m.get("id")
            text = str(m.get("text") or "").strip()
            if not text:
                continue
            
            embedding = self.llm.embed(text)
            vectors.append((mid, embedding, {"text": text}))
        
        # Upsert in batches
        index.upsert(vectors)
    
    def top_k(self, query: str, k: int = 20):
        qv = self.llm.embed(query)
        results = index.query(qv, top_k=k, include_metadata=True)
        return [(r.id, r.metadata['text'], r.score) for r in results.matches]
```

**Cost:** ~$70/month for Pinecone starter

---

## Recommended Solution: Option 2 (Firestore) → Option 1 (Redis) for Scale

### Why Firestore First?

1. **No new infrastructure:** Already using Firestore
2. **Simple migration:** Can upgrade to Redis later
3. **Good enough:** 30-50ms cache lookup acceptable for AI operations (2-5s total)
4. **Cost-effective:** Saves embedding costs with minimal overhead
5. **MVP-appropriate:** Balances simplicity and performance

### Migration Path to Redis

Once traffic increases or latency becomes critical:
- Redis offers 10x faster lookups
- Worth the infrastructure cost at scale
- Simple code swap (same interface)

---

## Implementation Steps

### Phase 1: Firestore Cache

1. **Create embeddings collection**
   ```typescript
   // Structure: /embeddings/{messageId}
   interface EmbeddingDoc {
     text: string;
     embedding: number[];  // Float array
     chatId: string;       // For cleanup
     createdAt: Timestamp;
   }
   ```

2. **Update RAGCache class**
   ```python
   # Replace in-memory dict with Firestore + L1 cache
   from .rag import RAGCache  # → FirestoreCachedRAG
   ```

3. **Add Firestore TTL**
   ```bash
   # Auto-delete embeddings after 7 days
   gcloud firestore fields ttls update createdAt \
     --collection-group=embeddings \
     --enable-ttl
   ```

4. **Update requirements.txt**
   ```
   google-cloud-firestore>=2.11.0
   ```

5. **Environment variable**
   ```bash
   FIRESTORE_PROJECT_ID=your-project-id
   ```

---

## Acceptance Criteria

- [ ] Embeddings persist across cold starts
- [ ] Cache hit rate > 80% after warm-up
- [ ] Latency increase < 100ms per request
- [ ] OpenAI embedding API calls reduced by 80%+
- [ ] L1 cache (in-memory) for hot data
- [ ] L2 cache (Firestore) for persistence
- [ ] TTL cleanup for old embeddings
- [ ] Metrics for cache hit/miss rates
- [ ] All existing tests pass
- [ ] Documentation updated

---

## Testing Checklist

```bash
# 1. Deploy service with Firestore cache
docker build -t messageai-langchain:cache .
docker run -p 8080:8080 \
  -e FIRESTORE_PROJECT_ID=xxx \
  -e OPENAI_API_KEY=xxx \
  messageai-langchain:cache

# 2. Make first request (cold cache)
# Expected: Generates embeddings, writes to Firestore
curl -X POST http://localhost:8080/sitrep/summarize \
  -H "Content-Type: application/json" \
  -d '{"requestId":"test1","context":{"chatId":"chat123"},"payload":{}}'

# 3. Make second request (warm cache)
# Expected: Reads from Firestore, no new embeddings
curl -X POST http://localhost:8080/sitrep/summarize \
  -H "Content-Type: application/json" \
  -d '{"requestId":"test2","context":{"chatId":"chat123"},"payload":{}}'

# 4. Verify Firestore documents
# Check /embeddings collection
# Expected: One document per message with embedding array

# 5. Restart container (cold start)
docker stop messageai-langchain
docker start messageai-langchain

# 6. Make third request
# Expected: L1 cache miss, L2 (Firestore) cache hit
```

---

## Performance Analysis

**Before (In-Memory Only):**
- Cold start: 2-5s (embedding generation)
- Warm: 2-3s (cache hits)
- Embedding API calls: 100% of messages

**After (Firestore Cache):**
- Cold start (first ever): 2-5s (generate + write Firestore)
- Cold start (cached): 2.5-3.5s (read Firestore + L1 cache)
- Warm (L1 cache): 2-3s (no Firestore)
- Embedding API calls: 10-20% of messages (only new ones)

**After (Redis Cache - Future):**
- Cold start: 2-3s (read Redis + L1 cache)
- Warm: 2-3s (L1 cache)
- Embedding API calls: 5-10% of messages

---

## Cost Analysis

### Current (No Cache)
```
1000 AI requests/day × 30 days = 30K requests/month
30K × 50 messages × 100 tokens = 150M tokens/month
Embedding cost: $3/month
Firestore reads: $0.06/month (30K × 50 × 2 = 3M reads)
Total: $3.06/month
```

### With Firestore Cache (80% hit rate)
```
Embedding cost: $0.60/month (20% of messages)
Firestore reads: $0.06/month (same)
Firestore writes: $0.27/month (30K × 50 × 0.2 = 300K writes)
Cache reads: $0.36/month (30K × 50 × 0.8 = 1.2M reads)
Total: $1.29/month
Savings: $1.77/month (58% reduction)
```

### At Scale (10K requests/day)
```
Current: $30/month
With cache: $12.90/month
Savings: $17.10/month (57% reduction)
```

---

## Related Files

### Files to Create
- `langchain-service/app/cache.py` - Firestore cache implementation

### Files to Modify
- `langchain-service/app/main.py` - Use cached RAG
- `langchain-service/app/rag.py` - Add cache layer
- `langchain-service/requirements.txt` - Add dependencies
- `langchain-service/README.md` - Document cache setup

### Related Documentation
- QC Report: `docs/reviews/BLOCKS_B_B2_QC_REPORT.md`
- Sprint 2 Task Plan: `docs/product/messageai-sprint2-task-plan.md`

---

## Additional Notes

### Cache Invalidation Strategy

**When to invalidate:**
- Message content changes (rare in chat apps)
- Embedding model changes (OpenAI updates)
- Manual purge for testing

**TTL Strategy:**
- Default: 7 days (tactical ops typically have short lifecycle)
- Configurable via environment variable
- Balance between freshness and cost

### Monitoring

Add metrics:
```python
# Custom metrics
cache_hits = 0
cache_misses = 0
embedding_api_calls = 0

# Log periodically
print(json.dumps({
    'cache_hit_rate': cache_hits / (cache_hits + cache_misses),
    'embedding_api_calls': embedding_api_calls,
    'timestamp': time.time()
}))
```

---

## Success Metrics

✅ **Definition of Done:**
1. Cache hit rate > 80% after warm-up
2. Embedding API calls reduced by 80%+
3. Latency increase < 100ms
4. Cost reduction of 50%+ at scale
5. All tests pass
6. Monitoring for cache metrics
7. Clean git commit with descriptive message

---

## References

- [Vector Database Comparison](https://www.pinecone.io/learn/vector-database/)
- [RAG Best Practices](https://www.anthropic.com/index/retrieval-augmented-generation)
- [Firestore TTL](https://cloud.google.com/firestore/docs/ttl)
- [Redis Vector Search](https://redis.io/docs/stack/search/reference/vectors/)

---

**Created by:** QC Agent (Blocks B & B2 Review)  
**Related Sprint:** Sprint 2 - AI Integration (Post-MVP Optimization)  
**Blocks:** None (enhancement)  
**Ticket ID:** LANGCHAIN-SERVICE-001

