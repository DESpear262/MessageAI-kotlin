package com.messageai.tactical.modules.facility

import com.google.firebase.firestore.*
import com.google.android.gms.tasks.Task
import io.mockk.*
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.Test
import com.google.common.truth.Truth.assertThat

class FacilityServiceTest {
    @Test
    fun `nearest returns closest available`() = runTest {
        val db = mockk<FirebaseFirestore>()
        val col = mockk<CollectionReference>()
        val snap = mockk<QuerySnapshot>()
        val task = mockk<Task<QuerySnapshot>>()
        val d1 = mockk<DocumentSnapshot>()
        val d2 = mockk<DocumentSnapshot>()
        every { db.collection(any()) } returns col
        every { col.limit(any()) } returns col
        every { col.get() } returns task
        coEvery { task.await() } returns snap
        every { snap.documents } returns listOf(d1, d2)
        every { d1.getString("name") } returns "A"
        every { d1.get("lat") } returns 0.5
        every { d1.get("lon") } returns 0.0
        every { d1.get("capabilities") } returns emptyList<String>()
        every { d1.getBoolean("available") } returns true
        every { d1.id } returns "f1"
        every { d2.getString("name") } returns "B"
        every { d2.get("lat") } returns 0.1
        every { d2.get("lon") } returns 0.0
        every { d2.get("capabilities") } returns emptyList<String>()
        every { d2.getBoolean("available") } returns true
        every { d2.id } returns "f2"

        val svc = FacilityService(db)
        val nearest = svc.nearest(0.0, 0.0)
        assertThat(nearest?.id).isEqualTo("f2")
    }
}

