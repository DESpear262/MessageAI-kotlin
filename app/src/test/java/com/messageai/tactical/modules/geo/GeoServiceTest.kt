package com.messageai.tactical.modules.geo

import android.content.Context
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.android.gms.location.FusedLocationProviderClient
import com.messageai.tactical.modules.ai.AIService
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Test
import com.google.common.truth.Truth.assertThat

class GeoServiceTest {
    @Test
    @org.junit.Ignore("Needs Robolectric shims for LocationServices; covered by integration")
    fun `analyzeChatThreats persists items from AI`() = runTest {
        val context = mockk<Context>(relaxed = true)
        val firestore = mockk<FirebaseFirestore>(relaxed = true)
        val auth = mockk<FirebaseAuth>(relaxed = true)
        val ai = mockk<AIService>()
        val service = GeoService(context, firestore, auth, ai)

        val threats = listOf(
            mapOf<String, Any?>("summary" to "Enemy contact", "severity" to 4, "radiusM" to 500, "geo" to mapOf("lat" to 0.0, "lon" to 0.0))
        )
        coEvery { ai.summarizeThreats(any(), any()) } returns Result.success(threats)
        val col = mockk<CollectionReference>(relaxed = true)
        every { firestore.collection(any()) } returns col
        every { col.add(any()) } returns com.google.android.gms.tasks.Tasks.forResult(mockk())

        val result = service.analyzeChatThreats("chat123", 1)
        verify { col.add(match { (it["summary"] as? String) == "Enemy contact" }) }
    }
}
