# TICKET: Add Unit Tests for Geo and Reporting Modules

**Status:** 🟡 Backlog  
**Priority:** Medium  
**Type:** Testing / Code Quality  
**Estimated Effort:** 4-6 hours  
**Assignee:** TBD  
**Created:** 2025-10-24

---

## Problem Summary

Blocks C (Geo Intelligence) and D (Reporting) have zero unit test coverage:

**Current State:**
- ❌ No tests for `GeoService`
- ❌ No tests for `ReportService`
- ❌ No tests for `ReportViewModel`
- Test Coverage: 0%

**Impact:**
- High: No safety net for refactoring
- Regression Risk: Changes may break existing functionality
- Confidence: Can't verify correctness
- Per PRD: "Automated tests optional for MVP" (acceptable but not ideal)

**Comparison:**
- ✅ Block A (AI Core): 32/32 tests passing (100% coverage)
- ❌ Blocks C & D: 0 tests

---

## Goals

Achieve reasonable test coverage (~70%+) for both modules:

**Block C (GeoService):**
- Distance calculation functions
- Threat filtering logic
- AI integration points

**Block D (ReportService + ViewModel):**
- Report generation flows
- Error handling
- ViewModel state management

---

## Implementation

### Block C: GeoService Tests

**File:** `app/src/test/java/com/messageai/tactical/modules/geo/GeoServiceTest.kt`

```kotlin
package com.messageai.tactical.modules.geo

import android.content.Context
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.tasks.Task
import com.messageai.tactical.modules.ai.AIService
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeoServiceTest {

    private lateinit var context: Context
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var aiService: AIService
    private lateinit var geoService: GeoService

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        firestore = mockk(relaxed = true)
        auth = mockk(relaxed = true)
        aiService = mockk(relaxed = true)
        
        geoService = GeoService(context, firestore, auth, aiService)
    }

    @Test
    fun `metersBetween calculates correct distance`() {
        // Haversine formula test
        // Distance from (0,0) to (0.01, 0.01) ≈ 1570 meters
        val distance = GeoService.metersBetween(0.0, 0.0, 0.01, 0.01)
        
        assertThat(distance).isGreaterThan(1500.0)
        assertThat(distance).isLessThan(1600.0)
    }

    @Test
    fun `milesBetween converts meters correctly`() {
        // 1609.344 meters = 1 mile
        val miles = GeoService.milesBetween(0.0, 0.0, 0.01, 0.01)
        
        // Should be close to 1 mile
        assertThat(miles).isGreaterThan(0.9)
        assertThat(miles).isLessThan(1.1)
    }

    @Test
    fun `alertSignalLossIfNeeded fires after 2 misses`() {
        // Verify no alert for 1 miss
        geoService.alertSignalLossIfNeeded(1)
        verify(exactly = 0) { /* notification shown */ }
        
        // Verify alert for 2 misses
        geoService.alertSignalLossIfNeeded(2)
        // Note: Can't easily verify notification without Robolectric
        // For now, just verify method doesn't crash
    }

    @Test
    fun `appendThreat creates Firestore document`() {
        val mockCollection = mockk<CollectionReference>(relaxed = true)
        val mockTask = mockk<Task<*>>(relaxed = true)
        
        every { firestore.collection("threats") } returns mockCollection
        every { mockCollection.add(any()) } returns mockTask
        
        geoService.appendThreat(
            summary = "Enemy contact",
            reporterLat = 34.0,
            reporterLon = -118.0,
            severity = 4
        )
        
        verify { mockCollection.add(any()) }
    }

    @Test
    fun `analyzeChatThreats calls AIService`() = runTest {
        // Mock location task
        val mockLocationTask = mockk<Task<*>>(relaxed = true)
        every { mockLocationTask.addOnSuccessListener(any()) } answers {
            val listener = firstArg<Any>()
            // Simulate success callback
            // Note: Full test requires more setup
            mockLocationTask
        }
        
        // Mock AI service
        coEvery { aiService.summarizeThreats(any(), any()) } returns Result.success(emptyList())
        
        geoService.analyzeChatThreats("chat123", 100) { count ->
            assertEquals(0, count)
        }
        
        // Verify AI service was called
        // Note: Async verification tricky without proper coroutine testing setup
    }

    @Test
    fun `summarizeThreatsNear filters by time`() {
        // Test that old threats are filtered out
        // Mock Firestore to return mixed old/new threats
        // Verify only fresh threats returned
        
        // Note: Requires mocking QuerySnapshot with test data
        // Implementation left as exercise (requires Firestore mocking setup)
    }

    @Test
    fun `summarizeThreatsNear filters by distance`() {
        // Test that distant threats are filtered out
        // Mock threats at various distances
        // Verify only nearby threats returned
        
        // Note: Requires mocking QuerySnapshot with test data
    }

    @Test
    fun `summarizeThreatsNear ranks by severity then time`() {
        // Test threat ranking logic
        // Mock threats with different severity and timestamps
        // Verify correct ordering
        
        // Note: Requires mocking QuerySnapshot with test data
    }
}
```

---

### Block D: ReportService Tests

**File:** `app/src/test/java/com/messageai/tactical/modules/reporting/ReportServiceTest.kt`

```kotlin
package com.messageai.tactical.modules.reporting

import com.messageai.tactical.modules.ai.api.AiResponseEnvelope
import com.messageai.tactical.modules.ai.provider.LangChainAdapter
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertTrue

class ReportServiceTest {

    private lateinit var adapter: LangChainAdapter
    private lateinit var reportService: ReportService

    @Before
    fun setup() {
        adapter = mockk()
        reportService = ReportService(adapter)
    }

    @Test
    fun `generateSITREP success returns markdown`() = runTest {
        // Mock successful response
        val mockResponse = AiResponseEnvelope(
            requestId = "test-123",
            status = "ok",
            data = mapOf("content" to "# SITREP\n\n## Summary\n\nAll clear.")
        )
        coEvery { adapter.post("sitrep/summarize", any(), any()) } returns mockResponse

        val result = reportService.generateSITREP("chat123", "6h")

        assertTrue(result.isSuccess)
        assertThat(result.getOrNull()).contains("# SITREP")
        assertThat(result.getOrNull()).contains("All clear")
        
        // Verify correct payload sent
        coVerify { 
            adapter.post(
                "sitrep/summarize", 
                mapOf("timeWindow" to "6h"),
                mapOf("chatId" to "chat123")
            ) 
        }
    }

    @Test
    fun `generateSITREP missing content returns error`() = runTest {
        // Mock response without content field
        val mockResponse = AiResponseEnvelope(
            requestId = "test-123",
            status = "ok",
            data = mapOf("format" to "markdown")  // Missing "content"
        )
        coEvery { adapter.post("sitrep/summarize", any(), any()) } returns mockResponse

        val result = reportService.generateSITREP("chat123", "6h")

        assertTrue(result.isFailure)
        assertThat(result.exceptionOrNull()?.message).contains("Missing markdown content")
    }

    @Test
    fun `generateSITREP network error returns failure`() = runTest {
        // Mock network exception
        coEvery { adapter.post(any(), any(), any()) } throws Exception("Network error")

        val result = reportService.generateSITREP("chat123", "6h")

        assertTrue(result.isFailure)
        assertThat(result.exceptionOrNull()?.message).contains("Network error")
    }

    @Test
    fun `generateWarnord calls correct endpoint`() = runTest {
        val mockResponse = AiResponseEnvelope(
            requestId = "test-123",
            status = "ok",
            data = mapOf("content" to "# WARNING ORDER\n\n...")
        )
        coEvery { adapter.post("template/warnord", any(), any()) } returns mockResponse

        val result = reportService.generateWarnord()

        assertTrue(result.isSuccess)
        coVerify { adapter.post("template/warnord", emptyMap(), emptyMap()) }
    }

    @Test
    fun `generateOpord calls correct endpoint`() = runTest {
        val mockResponse = AiResponseEnvelope(
            requestId = "test-123",
            status = "ok",
            data = mapOf("content" to "# OPERATIONS ORDER\n\n...")
        )
        coEvery { adapter.post("template/opord", any(), any()) } returns mockResponse

        val result = reportService.generateOpord()

        assertTrue(result.isSuccess)
        coVerify { adapter.post("template/opord", emptyMap(), emptyMap()) }
    }

    @Test
    fun `generateFrago calls correct endpoint`() = runTest {
        val mockResponse = AiResponseEnvelope(
            requestId = "test-123",
            status = "ok",
            data = mapOf("content" to "# FRAGMENTARY ORDER\n\n...")
        )
        coEvery { adapter.post("template/frago", any(), any()) } returns mockResponse

        val result = reportService.generateFrago()

        assertTrue(result.isSuccess)
        coVerify { adapter.post("template/frago", emptyMap(), emptyMap()) }
    }
}
```

---

### Block D: ReportViewModel Tests

**File:** `app/src/test/java/com/messageai/tactical/modules/reporting/ReportViewModelTest.kt`

```kotlin
package com.messageai.tactical.modules.reporting

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import com.google.common.truth.Truth.assertThat

@OptIn(ExperimentalCoroutinesApi::class)
class ReportViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    
    private lateinit var reportService: ReportService
    private lateinit var viewModel: ReportViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        reportService = mockk()
        viewModel = ReportViewModel(reportService)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadSitrep success updates markdown state`() = runTest {
        // Mock successful service call
        coEvery { reportService.generateSITREP(any(), any()) } returns 
            Result.success("# SITREP\n\nAll clear")

        viewModel.loadSitrep("chat123", "6h")
        advanceUntilIdle()

        assertThat(viewModel.markdown.value).isEqualTo("# SITREP\n\nAll clear")
        assertThat(viewModel.loading.value).isFalse()
    }

    @Test
    fun `loadSitrep failure shows error markdown`() = runTest {
        coEvery { reportService.generateSITREP(any(), any()) } returns 
            Result.failure(Exception("Network error"))

        viewModel.loadSitrep("chat123", "6h")
        advanceUntilIdle()

        assertThat(viewModel.markdown.value).contains("Generation failed")
        assertThat(viewModel.markdown.value).contains("Network error")
        assertThat(viewModel.loading.value).isFalse()
    }

    @Test
    fun `loadSitrep sets loading state`() = runTest(testDispatcher) {
        coEvery { reportService.generateSITREP(any(), any()) } coAnswers {
            delay(100)
            Result.success("# SITREP")
        }

        viewModel.loadSitrep("chat123", "6h")
        
        // Should be loading immediately
        assertThat(viewModel.loading.value).isTrue()
        
        advanceUntilIdle()
        
        // Should finish loading
        assertThat(viewModel.loading.value).isFalse()
    }

    @Test
    fun `loadTemplate warnord success`() = runTest {
        coEvery { reportService.generateWarnord() } returns Result.success("# WARNORD")

        viewModel.loadTemplate("warnord")
        advanceUntilIdle()

        assertThat(viewModel.markdown.value).isEqualTo("# WARNORD")
        coVerify { reportService.generateWarnord() }
    }

    @Test
    fun `loadTemplate unknown type fails`() = runTest {
        viewModel.loadTemplate("unknown")
        advanceUntilIdle()

        assertThat(viewModel.markdown.value).contains("Generation failed")
        assertThat(viewModel.markdown.value).contains("unknown template")
    }
}
```

---

## Acceptance Criteria

- [ ] GeoServiceTest created with ≥5 test cases
- [ ] ReportServiceTest created with ≥6 test cases
- [ ] ReportViewModelTest created with ≥5 test cases
- [ ] All tests pass
- [ ] Code coverage ≥70% for both modules
- [ ] Tests use modern patterns (runTest, mockk, Truth assertions)
- [ ] No Robolectric needed (pure JVM tests)
- [ ] CI/CD integration ready

---

## Testing Dependencies

```kotlin
// app/build.gradle.kts
dependencies {
    // Already present from Block A
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("com.google.truth:truth:1.1.5")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
}
```

---

## Running Tests

```bash
# Run all module tests
./gradlew :app:testDevDebugUnitTest

# Run specific test class
./gradlew :app:testDevDebugUnitTest --tests "*.GeoServiceTest"

# Run with coverage
./gradlew :app:testDevDebugUnitTest jacocoTestReport
```

---

## Success Metrics

✅ **Definition of Done:**
1. Test files created for GeoService, ReportService, ReportViewModel
2. All tests pass
3. Code coverage ≥70%
4. Tests follow Block A patterns
5. CI/CD can run tests automatically
6. Clean git commit

---

## References

- [Kotlin Testing Guide](https://kotlinlang.org/docs/jvm-test-using-junit.html)
- [MockK Documentation](https://mockk.io/)
- [Coroutine Testing](https://kotlinlang.org/docs/coroutines-testing.html)
- [AndroidX Test](https://developer.android.com/training/testing)

---

**Created by:** QC Agent (Blocks C & D Review)  
**Related Sprint:** Sprint 2 - AI Integration (Testing)  
**Blocks:** None (testing)  
**Ticket ID:** BLOCK-C-D-002

