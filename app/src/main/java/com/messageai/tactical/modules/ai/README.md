# AI Core Module - Block A

## Overview

The AI Core Module provides a provider-agnostic interface for integrating AI capabilities into MessageAI. It supports LangChain-based workflows for tactical operations including template generation, threat analysis, and multi-step agent workflows.

## Architecture

```
┌─────────────────┐
│   AIService     │ ← Facade for AI operations
└────────┬────────┘
         │
    ┌────┴────┬──────────────┬─────────────┐
    │         │              │             │
┌───▼────┐ ┌─▼─────────┐ ┌──▼────────┐ ┌─▼──────────┐
│Provider│ │LangChain  │ │RAG Context│ │WorkManager │
│ (DI)   │ │ Adapter   │ │ Builder   │ │ Worker     │
└────────┘ └───────────┘ └───────────┘ └────────────┘
```

## Components

### Core Interface

```kotlin
interface IAIProvider {
    suspend fun generateTemplate(type: String, context: String): Result<Map<String, Any?>>
    suspend fun extractGeoData(text: String): Result<Map<String, Any?>>
    suspend fun summarizeThreats(messages: List<MessageEntity>): Result<List<Map<String, Any?>>>
    suspend fun detectIntent(messages: List<MessageEntity>): Result<Map<String, Any?>>
    suspend fun runWorkflow(endpoint: String, payload: Map<String, Any?>): Result<Map<String, Any?>>
}
```

### AIService (Facade)

Central entry point for all AI operations. Handles context building and provider delegation.

**Usage:**
```kotlin
@Inject lateinit var aiService: AIService

// Generate a NATO template
val result = aiService.generateTemplate(
    chatId = "chat123",
    type = "MEDEVAC",
    maxMessages = 50
)

// Extract geolocation
val geoData = aiService.extractGeoData("Position at grid 123456")

// Detect intent
val intent = aiService.detectIntent(chatId = "chat123", minMessages = 20)
```

### LangChain Adapter

Routes requests to LangChain service endpoints via Firebase Cloud Function proxy.

**Endpoints:**
- `POST /template/generate` - NATO template autofill
- `POST /threats/extract` - Threat summarization
- `POST /sitrep/summarize` - SITREP generation
- `POST /intent/casevac/detect` - CASEVAC intent detection
- `POST /workflow/casevac/run` - Multi-step CASEVAC agent

**Request Envelope:**
```kotlin
{
  "requestId": "uuid-here",
  "context": { "chatId": "chat123" },
  "payload": { ... }
}
```

### RAG Context Builder

Fetches message history from Room and formats for RAG workflows.

**Configuration (`RagConfig.kt`):**
- Token budgets for different tasks
- Window sizes
- Overlap settings

**Usage:**
```kotlin
val context = contextBuilder.build(
    RagContextBuilder.WindowSpec(
        chatId = "chat123",
        maxMessages = 50,
        includeGeo = true
    )
)
```

### WorkManager Integration

Background processing for AI workflows with automatic retry and network awareness.

**Usage:**
```kotlin
AIWorkflowWorker.enqueue(
    context = context,
    endpoint = "workflow/casevac/run",
    args = mapOf("priority" to "urgent")
)
```

**Features:**
- Network-aware (only runs when connected)
- Exponential backoff (30s initial)
- Unique work policy
- Hilt-integrated

### Providers

#### LocalProvider (Development/Testing)

Returns mock responses for offline development. Never fails.

**When to use:**
- Local development without backend
- UI testing
- Integration tests

#### Remote Provider (Future)

Will connect to actual LangChain service when Block B/B2 are complete.

## Configuration

### Build Config

Set in `app/build.gradle.kts`:

```kotlin
buildConfigField("String", "CF_BASE_URL", "\"https://your-cf-url/\"")
buildConfigField("boolean", "AI_ENABLED", "true")
```

### Hilt Module

AI components are provided via `AIModule.kt`:
- OkHttp with Firebase Auth interceptor
- Retrofit with Moshi converter
- LangChain API and adapter
- RAG context builder
- AI service facade

## Security

### Authentication

- Firebase ID tokens automatically injected via OkHttp interceptor
- Tokens refreshed as needed
- No API keys exposed to client

### Data Privacy

- Generic Map types prevent schema leakage
- Request IDs for audit trails
- No PII logged

## Testing

### Unit Tests

- `AIServiceTest.kt` - Service facade tests
- `LocalProviderTest.kt` - Mock provider tests
- `RagContextBuilderTest.kt` - Context building tests
- `LangChainAdapterTest.kt` - HTTP adapter tests

### Running Tests

```bash
./gradlew :app:testDevDebugUnitTest --tests "com.messageai.tactical.modules.ai.*"
```

### Test Dependencies

```kotlin
testImplementation("io.mockk:mockk:1.13.8")
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
```

## Usage Examples

### Template Generation

```kotlin
viewModelScope.launch {
    aiService.generateTemplate(
        chatId = currentChatId,
        type = "MEDEVAC",
        maxMessages = 50
    ).onSuccess { template ->
        // Display template to user
        showTemplate(template)
    }.onFailure { error ->
        // Handle error
        showError(error.message)
    }
}
```

### Threat Analysis

```kotlin
suspend fun analyzeThreatsChatId) {
    aiService.summarizeThreats(chatId, maxMessages = 100)
        .onSuccess { threats ->
            threats.forEach { threat ->
                val summary = threat["summary"] as String
                val severity = threat["severity"] as Int
                displayThreat(summary, severity)
            }
        }
}
```

### Background Workflow

```kotlin
AIWorkflowWorker.enqueue(
    context = applicationContext,
    endpoint = "workflow/casevac/run",
    args = mapOf(
        "chatId" to chatId,
        "priority" to "urgent"
    )
)
```

## Integration with Existing Code

### Room Database

AI module integrates with existing `MessageDao`:

```kotlin
suspend fun getLastMessages(chatId: String, limit: Int): List<MessageEntity>
```

### Firebase Auth

Automatic token injection via interceptor:

```kotlin
val token = auth.currentUser?.getIdToken(false)?.await()?.token
builder.addHeader("Authorization", "Bearer $token")
```

### Hilt DI

Inject AI service into ViewModels or Repositories:

```kotlin
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val aiService: AIService
) : ViewModel()
```

## Next Steps (Block B/B2)

1. Deploy Firebase Cloud Function proxy
2. Deploy LangChain Python service  
3. Update `CF_BASE_URL` with actual endpoint
4. Integration testing with live service
5. Performance tuning and caching

## Configuration Checklist

- [ ] Update `CF_BASE_URL` in `build.gradle.kts`
- [ ] Deploy Firebase Cloud Function
- [ ] Deploy LangChain service
- [ ] Configure service environment variables
- [ ] Test end-to-end workflow
- [ ] Monitor latency and errors

## Troubleshooting

### "Unresolved reference IAIProvider"

Rebuild project:
```bash
./gradlew clean build
```

### "No implementation for IAIProvider"

Check `AIModule.kt` has `@InstallIn(SingletonComponent::class)`

### Network errors

1. Check `CF_BASE_URL` is correct
2. Verify Firebase Auth token is valid
3. Check network constraints in WorkManager

### Compilation errors

Ensure dependencies in `app/build.gradle.kts`:
- Retrofit 2.11.0
- Moshi 1.15.1
- OkHttp 4.12.0

## Performance Targets

| Operation | Target | Notes |
|-----------|--------|-------|
| Template generation | < 3s | With RAG context |
| Geo extraction | < 1s | Simple NLP |
| Threat summary | < 5s | 100 messages |
| Intent detection | < 2s | 20 message window |
| CASEVAC workflow | < 60s | Full multi-step |

## Observability

### Request IDs

Every request gets a UUID for tracing:
```kotlin
val requestId = UUID.randomUUID().toString()
```

### Logging

Enable debug logging in `build.gradle.kts`:
```kotlin
buildConfigField("boolean", "DEBUG", "true")
```

OkHttp interceptor logs:
- Request/response bodies (DEBUG only)
- Headers
- Timing

## Provider Swap Guide

To replace LocalProvider with a real implementation:

1. Create new provider class implementing `IAIProvider`
2. Update `AIModule.kt`:
```kotlin
@Provides
@Singleton
fun provideIAIProvider(adapter: LangChainAdapter): IAIProvider {
    return if (BuildConfig.AI_ENABLED) {
        LangChainProvider(adapter) // Your new provider
    } else {
        LocalProvider()
    }
}
```

3. No changes needed elsewhere - DI handles it!

## License & Credits

Part of MessageAI Kotlin Android implementation.  
See main project README for license information.

