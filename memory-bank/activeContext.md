# Active Context

## Current Focus
- Block A: AI Core module ✅ COMPLETE & QC TESTED
- Ready to proceed to Block B (Firebase Function Proxy) and Block B2 (LangChain Service)

## Recent Changes
- Block A AI Core Module completed:
  - `RagConfig` and `RagContextBuilder` (configurable window sizes per task)
  - Interfaces and services: `IAIProvider`, `AIService`
  - Networking: Retrofit `LangChainApi` + `LangChainAdapter` with all 5 endpoints
  - Providers: `LocalProvider` (offline testing)
  - DI: `AIModule` with OkHttp (Firebase Auth + logging), Retrofit, Hilt bindings
  - Worker: `AIWorkflowWorker` with network constraints + exponential backoff
  - Tests: 32 comprehensive unit tests (100% pass rate)
- QC Review completed:
  - All Block A code compiles cleanly
  - All acceptance criteria met
  - Security review passed
  - Documentation added (QC report + developer README)
- Pre-existing test issues fixed (unrelated to Block A):
  - Google Truth import package corrected in 5 test files
  - RootViewModel tests updated for presence service parameter
  - Test suite: 98% pass rate (71/72 tests)
- Dependencies added: Retrofit, Moshi, OkHttp, test libraries (MockK, Truth)

## Next Steps
- Block B: Deploy Firebase Cloud Function proxy for LangChain routing
- Block B2: Deploy LangChain Python service with RAG + agent workflows
- Update `CF_BASE_URL` in build.gradle once endpoints deployed
- Integration testing with live LangChain service

## Risks
- Large images can lead to longer uploads (indeterminate spinner only for MVP).

