package com.messageai.tactical.modules.reporting

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import com.google.common.truth.Truth.assertThat

@OptIn(ExperimentalCoroutinesApi::class)
class ReportViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadSitrep success updates markdown`() = runTest(dispatcher) {
        val service = mockk<ReportService>()
        coEvery { service.generateSITREP(any(), any()) } returns Result.success("# SITREP\nOK")
        val vm = ReportViewModel(service)
        vm.loadSitrep("chat123", "6h")
        advanceUntilIdle()
        assertThat(vm.markdown.value).isEqualTo("# SITREP\nOK")
        assertThat(vm.loading.value).isFalse()
    }

    @Test
    fun `loadSitrep failure shows error markdown`() = runTest(dispatcher) {
        val service = mockk<ReportService>()
        coEvery { service.generateSITREP(any(), any()) } returns Result.failure(Exception("Network error"))
        val vm = ReportViewModel(service)
        vm.loadSitrep("chat123", "6h")
        advanceUntilIdle()
        assertThat(vm.markdown.value).contains("Generation failed")
        assertThat(vm.loading.value).isFalse()
    }
}

