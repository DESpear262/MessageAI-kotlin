# TICKET: Add Unit Tests for Mission Tracker and CASEVAC Modules

**Status:** 🟡 Backlog  
**Priority:** High  
**Type:** Testing / Code Quality  
**Estimated Effort:** 6-8 hours  
**Assignee:** TBD  
**Created:** 2025-10-24

---

## Problem Summary

Blocks E (Mission Tracker) and F (CASEVAC Agent) have zero unit test coverage:

**Current State:**
- ❌ No tests for `MissionService`
- ❌ No tests for `MissionBoardViewModel`
- ❌ No tests for `CasevacWorker`
- ❌ No tests for `FacilityService`
- Test Coverage: 0%

**Impact:**
- High: No safety net for refactoring
- Regression Risk: Changes may break existing functionality
- Confidence: Can't verify correctness
- Per PRD: "Automated tests optional for MVP" (acceptable but not ideal)

**Comparison:**
- ✅ Block A (AI Core): 32/32 tests passing (100% coverage)
- ❌ Blocks E & F: 0 tests

---

## Goals

Achieve reasonable test coverage (~70%+) for both modules:

**Block E (Mission Tracker):**
- MissionService CRUD operations
- MissionService real-time Flow
- Mission archiving logic
- AI task extraction

**Block F (CASEVAC Workflow):**
- CasevacWorker execution
- FacilityService distance calculation
- FacilityService nearest lookup

---

## Implementation

### Block E: MissionService Tests

**File:** `app/src/test/java/com/messageai/tactical/modules/missions/MissionServiceTest.kt`

```kotlin
package com.messageai.tactical.modules.missions

import com.google.firebase.firestore.*
import com.google.android.gms.tasks.Task
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.tasks.await
import org.junit.Before
import org.junit.Test
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MissionServiceTest {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var missionService: MissionService

    @Before
    fun setup() {
        firestore = mockk(relaxed = true)
        missionService = MissionService(firestore)
    }

    @Test
    fun `createMission returns document ID`() = runTest {
        val mockCollection = mockk<CollectionReference>(relaxed = true)
        val mockDocument = mockk<DocumentReference>(relaxed = true)
        val mockTask = mockk<Task<Void>>(relaxed = true)
        
        every { firestore.collection("missions") } returns mockCollection
        every { mockCollection.document() } returns mockDocument
        every { mockDocument.id } returns "mission-123"
        every { mockDocument.set(any()) } returns mockTask
        coEvery { mockTask.await() } returns mockk()

        val mission = Mission(
            chatId = "chat-test",
            title = "Test Mission",
            status = "open",
            priority = 3
        )
        
        val id = missionService.createMission(mission)
        
        assertEquals("mission-123", id)
        verify { mockDocument.set(any()) }
    }

    @Test
    fun `createMission filters null values`() = runTest {
        val mockCollection = mockk<CollectionReference>(relaxed = true)
        val mockDocument = mockk<DocumentReference>(relaxed = true)
        val mockTask = mockk<Task<Void>>(relaxed = true)
        
        every { firestore.collection("missions") } returns mockCollection
        every { mockCollection.document() } returns mockDocument
        every { mockDocument.id } returns "mission-123"
        coEvery { mockTask.await() } returns mockk()
        
        val capturedData = slot<Map<String, Any?>>()
        every { mockDocument.set(capture(capturedData)) } returns mockTask

        val mission = Mission(
            chatId = "chat-test",
            title = "Test",
            description = null,  // Should be filtered out
            tags = null          // Should be filtered out
        )
        
        missionService.createMission(mission)
        
        // Verify null fields not included
        assertThat(capturedData.captured.containsKey("description")).isFalse()
        assertThat(capturedData.captured.containsKey("tags")).isFalse()
        assertThat(capturedData.captured["title"]).isEqualTo("Test")
    }

    @Test
    fun `addTask returns task ID`() = runTest {
        val mockMissionsCol = mockk<CollectionReference>(relaxed = true)
        val mockMissionDoc = mockk<DocumentReference>(relaxed = true)
        val mockTasksCol = mockk<CollectionReference>(relaxed = true)
        val mockTaskDoc = mockk<DocumentReference>(relaxed = true)
        val mockTask = mockk<Task<Void>>(relaxed = true)
        
        every { firestore.collection("missions") } returns mockMissionsCol
        every { mockMissionsCol.document("mission-123") } returns mockMissionDoc
        every { mockMissionDoc.collection("tasks") } returns mockTasksCol
        every { mockTasksCol.document() } returns mockTaskDoc
        every { mockTaskDoc.id } returns "task-456"
        every { mockTaskDoc.set(any()) } returns mockTask
        coEvery { mockTask.await() } returns mockk()

        val task = MissionTask(
            missionId = "mission-123",
            title = "Test Task",
            status = "open",
            priority = 2
        )
        
        val id = missionService.addTask("mission-123", task)
        
        assertEquals("task-456", id)
    }

    @Test
    fun `updateMission updates Firestore document`() = runTest {
        val mockCollection = mockk<CollectionReference>(relaxed = true)
        val mockDocument = mockk<DocumentReference>(relaxed = true)
        val mockTask = mockk<Task<Void>>(relaxed = true)
        
        every { firestore.collection("missions") } returns mockCollection
        every { mockCollection.document("mission-123") } returns mockDocument
        every { mockDocument.update(any<Map<String, Any?>>()) } returns mockTask
        coEvery { mockTask.await() } returns mockk()

        missionService.updateMission("mission-123", mapOf("status" to "done"))
        
        verify { mockDocument.update(match<Map<String, Any?>> { it["status"] == "done" }) }
    }

    @Test
    fun `incrementCasevacCasualties finds and updates mission`() = runTest {
        val mockCollection = mockk<CollectionReference>(relaxed = true)
        val mockQuery = mockk<Query>(relaxed = true)
        val mockQuerySnapshot = mockk<QuerySnapshot>(relaxed = true)
        val mockDocument = mockk<DocumentSnapshot>(relaxed = true)
        val mockDocRef = mockk<DocumentReference>(relaxed = true)
        val mockGetTask = mockk<Task<QuerySnapshot>>(relaxed = true)
        val mockUpdateTask = mockk<Task<Void>>(relaxed = true)
        
        every { firestore.collection("missions") } returns mockCollection
        every { mockCollection.whereEqualTo("chatId", "chat-test") } returns mockQuery
        every { mockQuery.whereEqualTo("archived", false) } returns mockQuery
        every { mockQuery.whereEqualTo("title", "CASEVAC") } returns mockQuery
        every { mockQuery.orderBy("updatedAt", Query.Direction.DESCENDING) } returns mockQuery
        every { mockQuery.limit(1) } returns mockQuery
        every { mockQuery.get() } returns mockGetTask
        coEvery { mockGetTask.await() } returns mockQuerySnapshot
        
        every { mockQuerySnapshot.documents } returns listOf(mockDocument)
        every { mockDocument.id } returns "mission-123"
        every { mockDocument.get("casevacCasualties") } returns 5
        
        every { mockCollection.document("mission-123") } returns mockDocRef
        every { mockDocRef.update(any<Map<String, Any?>>()) } returns mockUpdateTask
        coEvery { mockUpdateTask.await() } returns mockk()

        missionService.incrementCasevacCasualties("chat-test", delta = 2)
        
        verify { 
            mockDocRef.update(match<Map<String, Any?>> { 
                it["casevacCasualties"] == 7  // 5 + 2
            }) 
        }
    }

    @Test
    fun `archiveIfCompleted archives when all tasks done`() = runTest {
        val mockMissionsCol = mockk<CollectionReference>(relaxed = true)
        val mockMissionDoc = mockk<DocumentReference>(relaxed = true)
        val mockTasksCol = mockk<CollectionReference>(relaxed = true)
        val mockQuery = mockk<Query>(relaxed = true)
        val mockQuerySnapshot = mockk<QuerySnapshot>(relaxed = true)
        val mockTaskDoc1 = mockk<DocumentSnapshot>(relaxed = true)
        val mockTaskDoc2 = mockk<DocumentSnapshot>(relaxed = true)
        val mockGetTask = mockk<Task<QuerySnapshot>>(relaxed = true)
        val mockUpdateTask = mockk<Task<Void>>(relaxed = true)
        
        every { firestore.collection("missions") } returns mockMissionsCol
        every { mockMissionsCol.document("mission-123") } returns mockMissionDoc
        every { mockMissionDoc.collection("tasks") } returns mockTasksCol
        every { mockTasksCol.get() } returns mockGetTask
        coEvery { mockGetTask.await() } returns mockQuerySnapshot
        
        // All tasks are done
        every { mockQuerySnapshot.documents } returns listOf(mockTaskDoc1, mockTaskDoc2)
        every { mockTaskDoc1.getString("status") } returns "done"
        every { mockTaskDoc2.getString("status") } returns "done"
        
        every { mockMissionDoc.update(any<Map<String, Any?>>()) } returns mockUpdateTask
        coEvery { mockUpdateTask.await() } returns mockk()

        missionService.archiveIfCompleted("mission-123")
        
        verify { 
            mockMissionDoc.update(match<Map<String, Any?>> { 
                it["archived"] == true 
            }) 
        }
    }

    @Test
    fun `archiveIfCompleted does not archive when tasks incomplete`() = runTest {
        val mockMissionsCol = mockk<CollectionReference>(relaxed = true)
        val mockMissionDoc = mockk<DocumentReference>(relaxed = true)
        val mockTasksCol = mockk<CollectionReference>(relaxed = true)
        val mockQuerySnapshot = mockk<QuerySnapshot>(relaxed = true)
        val mockTaskDoc1 = mockk<DocumentSnapshot>(relaxed = true)
        val mockTaskDoc2 = mockk<DocumentSnapshot>(relaxed = true)
        val mockGetTask = mockk<Task<QuerySnapshot>>(relaxed = true)
        
        every { firestore.collection("missions") } returns mockMissionsCol
        every { mockMissionsCol.document("mission-123") } returns mockMissionDoc
        every { mockMissionDoc.collection("tasks") } returns mockTasksCol
        every { mockTasksCol.get() } returns mockGetTask
        coEvery { mockGetTask.await() } returns mockQuerySnapshot
        
        // One task still in progress
        every { mockQuerySnapshot.documents } returns listOf(mockTaskDoc1, mockTaskDoc2)
        every { mockTaskDoc1.getString("status") } returns "done"
        every { mockTaskDoc2.getString("status") } returns "in_progress"

        missionService.archiveIfCompleted("mission-123")
        
        // Should NOT call update
        verify(exactly = 0) { mockMissionDoc.update(any<Map<String, Any?>>()) }
    }
}
```

---

### Block F: FacilityService Tests

**File:** `app/src/test/java/com/messageai/tactical/modules/facility/FacilityServiceTest.kt`

```kotlin
package com.messageai.tactical.modules.facility

import com.google.firebase.firestore.*
import com.google.android.gms.tasks.Task
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.tasks.await
import org.junit.Before
import org.junit.Test
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FacilityServiceTest {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var facilityService: FacilityService

    @Before
    fun setup() {
        firestore = mockk(relaxed = true)
        facilityService = FacilityService(firestore)
    }

    @Test
    fun `nearest returns closest available facility`() = runTest {
        val mockCollection = mockk<CollectionReference>(relaxed = true)
        val mockQuery = mockk<Query>(relaxed = true)
        val mockQuerySnapshot = mockk<QuerySnapshot>(relaxed = true)
        val mockGetTask = mockk<Task<QuerySnapshot>>(relaxed = true)
        
        // Create mock facility documents
        val facility1 = mockk<DocumentSnapshot>(relaxed = true)
        val facility2 = mockk<DocumentSnapshot>(relaxed = true)
        val facility3 = mockk<DocumentSnapshot>(relaxed = true)
        
        every { firestore.collection("facilities") } returns mockCollection
        every { mockCollection.limit(500) } returns mockQuery
        every { mockQuery.get() } returns mockGetTask
        coEvery { mockGetTask.await() } returns mockQuerySnapshot
        
        // Facility 1: 10 miles away (lat 32.0, lon -118.0)
        every { facility1.id } returns "fac-1"
        every { facility1.getString("name") } returns "Facility A"
        every { facility1.get("lat") } returns 32.0
        every { facility1.get("lon") } returns -118.0
        every { facility1.get("capabilities") } returns listOf("trauma")
        every { facility1.getBoolean("available") } returns true
        
        // Facility 2: 5 miles away (lat 31.5, lon -118.0)
        every { facility2.id } returns "fac-2"
        every { facility2.getString("name") } returns "Facility B"
        every { facility2.get("lat") } returns 31.5
        every { facility2.get("lon") } returns -118.0
        every { facility2.get("capabilities") } returns listOf("surgery")
        every { facility2.getBoolean("available") } returns true
        
        // Facility 3: 3 miles away but unavailable
        every { facility3.id } returns "fac-3"
        every { facility3.getString("name") } returns "Facility C"
        every { facility3.get("lat") } returns 31.3
        every { facility3.get("lon") } returns -118.0
        every { facility3.get("capabilities") } returns listOf("medevac")
        every { facility3.getBoolean("available") } returns false
        
        every { mockQuerySnapshot.documents } returns listOf(facility1, facility2, facility3)
        
        // Search from lat 31.0, lon -118.0
        val nearest = facilityService.nearest(31.0, -118.0, requireAvailable = true)
        
        // Should return Facility B (closest available)
        assertThat(nearest?.name).isEqualTo("Facility B")
        assertThat(nearest?.id).isEqualTo("fac-2")
    }

    @Test
    fun `nearest returns null when no facilities available`() = runTest {
        val mockCollection = mockk<CollectionReference>(relaxed = true)
        val mockQuery = mockk<Query>(relaxed = true)
        val mockQuerySnapshot = mockk<QuerySnapshot>(relaxed = true)
        val mockGetTask = mockk<Task<QuerySnapshot>>(relaxed = true)
        
        every { firestore.collection("facilities") } returns mockCollection
        every { mockCollection.limit(500) } returns mockQuery
        every { mockQuery.get() } returns mockGetTask
        coEvery { mockGetTask.await() } returns mockQuerySnapshot
        every { mockQuerySnapshot.documents } returns emptyList()
        
        val nearest = facilityService.nearest(31.0, -118.0)
        
        assertNull(nearest)
    }

    @Test
    fun `nearest includes unavailable when requireAvailable is false`() = runTest {
        val mockCollection = mockk<CollectionReference>(relaxed = true)
        val mockQuery = mockk<Query>(relaxed = true)
        val mockQuerySnapshot = mockk<QuerySnapshot>(relaxed = true)
        val mockGetTask = mockk<Task<QuerySnapshot>>(relaxed = true)
        
        val facility = mockk<DocumentSnapshot>(relaxed = true)
        
        every { firestore.collection("facilities") } returns mockCollection
        every { mockCollection.limit(500) } returns mockQuery
        every { mockQuery.get() } returns mockGetTask
        coEvery { mockGetTask.await() } returns mockQuerySnapshot
        
        every { facility.id } returns "fac-1"
        every { facility.getString("name") } returns "Facility A"
        every { facility.get("lat") } returns 31.5
        every { facility.get("lon") } returns -118.0
        every { facility.get("capabilities") } returns listOf("trauma")
        every { facility.getBoolean("available") } returns false
        
        every { mockQuerySnapshot.documents } returns listOf(facility)
        
        val nearest = facilityService.nearest(31.0, -118.0, requireAvailable = false)
        
        assertThat(nearest?.name).isEqualTo("Facility A")
    }
}
```

---

### Block F: CasevacWorker Tests

**File:** `app/src/test/java/com/messageai/tactical/modules/ai/work/CasevacWorkerTest.kt`

```kotlin
package com.messageai.tactical.modules.ai.work

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.messageai.tactical.modules.ai.AIService
import com.messageai.tactical.modules.facility.Facility
import com.messageai.tactical.modules.facility.FacilityService
import com.messageai.tactical.modules.missions.Mission
import com.messageai.tactical.modules.missions.MissionService
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertEquals

class CasevacWorkerTest {

    private lateinit var context: Context
    private lateinit var workerParams: WorkerParameters
    private lateinit var aiService: AIService
    private lateinit var missionService: MissionService
    private lateinit var facilityService: FacilityService
    private lateinit var worker: CasevacWorker

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        workerParams = mockk(relaxed = true)
        aiService = mockk(relaxed = true)
        missionService = mockk(relaxed = true)
        facilityService = mockk(relaxed = true)
        
        every { workerParams.inputData } returns workDataOf(
            "chatId" to "chat-test",
            "messageId" to "msg-123"
        )
        
        worker = CasevacWorker(context, workerParams, aiService, missionService, facilityService)
    }

    @Test
    fun `doWork executes full workflow and returns success`() = runTest {
        // Mock successful AI call
        coEvery { 
            aiService.generateTemplate(any(), any(), any()) 
        } returns Result.success(mapOf("type" to "MEDEVAC"))
        
        // Mock facility lookup
        val testFacility = Facility(
            id = "fac-1",
            name = "Test Hospital",
            lat = 31.0,
            lon = -118.0,
            capabilities = listOf("surgery"),
            available = true
        )
        coEvery { 
            facilityService.nearest(any(), any()) 
        } returns testFacility
        
        // Mock mission creation
        coEvery { 
            missionService.createMission(any()) 
        } returns "mission-123"
        
        coEvery { 
            missionService.incrementCasevacCasualties(any(), any()) 
        } just Runs
        
        coEvery { 
            missionService.updateMission(any(), any()) 
        } just Runs
        
        coEvery { 
            missionService.archiveIfCompleted(any()) 
        } just Runs
        
        val result = worker.doWork()
        
        assertEquals(ListenableWorker.Result.success(), result)
        
        // Verify all steps executed
        coVerify { aiService.generateTemplate("chat-test", "MEDEVAC", 50) }
        coVerify { facilityService.nearest(any(), any()) }
        coVerify { missionService.createMission(any()) }
        coVerify { missionService.incrementCasevacCasualties("chat-test", 1) }
        coVerify { missionService.updateMission("mission-123", mapOf("status" to "done")) }
        coVerify { missionService.archiveIfCompleted("mission-123") }
    }

    @Test
    fun `doWork returns retry on exception`() = runTest {
        // Mock AI failure
        coEvery { 
            aiService.generateTemplate(any(), any(), any()) 
        } throws Exception("Network error")
        
        val result = worker.doWork()
        
        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun `doWork returns failure when chatId missing`() = runTest {
        every { workerParams.inputData } returns workDataOf()  // No chatId
        
        val result = worker.doWork()
        
        assertEquals(ListenableWorker.Result.failure(), result)
        
        // Should not execute any workflow steps
        coVerify(exactly = 0) { aiService.generateTemplate(any(), any(), any()) }
    }
}
```

---

## Acceptance Criteria

- [ ] MissionServiceTest created with ≥8 test cases
- [ ] FacilityServiceTest created with ≥3 test cases
- [ ] CasevacWorkerTest created with ≥3 test cases
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
    testImplementation("androidx.work:work-testing:2.9.0")  // For WorkManager testing
}
```

---

## Running Tests

```bash
# Run all module tests
./gradlew :app:testDevDebugUnitTest

# Run specific test class
./gradlew :app:testDevDebugUnitTest --tests "*.MissionServiceTest"

# Run with coverage
./gradlew :app:testDevDebugUnitTest jacocoTestReport
```

---

## Success Metrics

✅ **Definition of Done:**
1. Test files created for MissionService, FacilityService, CasevacWorker
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
- [WorkManager Testing](https://developer.android.com/topic/libraries/architecture/workmanager/how-to/integration-testing)
- Block A Tests: `app/src/test/java/com/messageai/tactical/modules/ai/`

---

**Created by:** QC Agent (Blocks E & F Review)  
**Related Sprint:** Sprint 2 - AI Integration (Testing)  
**Blocks:** E, F  
**Ticket ID:** BLOCK-E-F-002

