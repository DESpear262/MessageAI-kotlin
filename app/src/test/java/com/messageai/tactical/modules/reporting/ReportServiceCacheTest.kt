package com.messageai.tactical.modules.reporting

import com.messageai.tactical.modules.ai.api.AiResponseEnvelope
import com.messageai.tactical.modules.ai.provider.LangChainAdapter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import com.google.common.truth.Truth.assertThat

class ReportServiceCacheTest {
    @Test
    fun `generateSITREP uses cache on second call`() = runTest {
        val adapter = mockk<LangChainAdapter>()
        val service = ReportService(adapter)
        val resp = AiResponseEnvelope(requestId = "id", status = "ok", data = mapOf<String, Any?>("content" to "# SITREP"))
        coEvery { adapter.post("sitrep/summarize", any(), any()) } returns resp

        val r1 = service.generateSITREP("chat123", "6h")
        val r2 = service.generateSITREP("chat123", "6h")

        assertThat(r1.isSuccess).isTrue()
        assertThat(r2.isSuccess).isTrue()
        coVerify(exactly = 1) { adapter.post("sitrep/summarize", any(), any()) }
    }
}
