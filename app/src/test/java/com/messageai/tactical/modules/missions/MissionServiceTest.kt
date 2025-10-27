package com.messageai.tactical.modules.missions

import com.google.firebase.firestore.*
import com.google.android.gms.tasks.Task
import io.mockk.*
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.Test
import com.google.common.truth.Truth.assertThat

class MissionServiceTest {
    @Test
    fun `createMission writes doc and returns id`() = runTest {
        val db = mockk<FirebaseFirestore>()
        val col = mockk<CollectionReference>()
        val doc = mockk<DocumentReference>()
        val setTask = mockk<Task<Void>>()
        every { db.collection(any()) } returns col
        every { col.document() } returns doc
        every { doc.id } returns "mission-1"
        every { doc.set(any()) } returns setTask
        coEvery { setTask.await() } returns mockk()

        val svc = MissionService(db)
        val id = svc.createMission(Mission(chatId = "chat1", title = "Op", status = "open", priority = 3))
        assertThat(id).isEqualTo("mission-1")
        verify { doc.set(any<Map<String, Any?>>()) }
    }

    @Test
    fun `updateMission updates fields`() = runTest {
        val db = mockk<FirebaseFirestore>()
        val col = mockk<CollectionReference>()
        val doc = mockk<DocumentReference>()
        val task = mockk<Task<Void>>()
        every { db.collection(any()) } returns col
        every { col.document("m1") } returns doc
        every { doc.update(any<Map<String, Any?>>()) } returns task
        coEvery { task.await() } returns mockk()

        val svc = MissionService(db)
        svc.updateMission("m1", mapOf("status" to "done"))
        verify { doc.update(match<Map<String, Any?>> { it["status"] == "done" }) }
    }
}

