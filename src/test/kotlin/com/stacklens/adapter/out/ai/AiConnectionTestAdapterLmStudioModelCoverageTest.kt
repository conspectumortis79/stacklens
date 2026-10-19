package com.stacklens.adapter.out.ai

import com.stacklens.application.port.out.LmStudioModelLookupPort
import com.stacklens.application.port.out.platform.ContainerRuntime
import com.stacklens.application.port.out.platform.ContainerRuntimeProbePort
import com.stacklens.domain.enums.AiProvider
import com.stacklens.domain.model.AiConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AiConnectionTestAdapterLmStudioModelCoverageTest {
    private val lmStudioService = mock<LmStudioModelLookupPort>()
    private val containerRuntimeProbe = mock<ContainerRuntimeProbePort>()
    private val adapter = AiConnectionTestAdapter(emptyList(), lmStudioService, containerRuntimeProbe)

    init {
        whenever(containerRuntimeProbe.detectActiveRuntime()).thenReturn(ContainerRuntime.NONE)
        whenever(lmStudioService.fetchAvailableModels(any())).thenReturn(listOf("first-model"))
    }

    @Test
    fun `a blank requested model falls back to the stored model of the lmstudio provider`() {
        assertEquals("stored-model", adapter.resolveLmStudioModel("  ", null, aiConfig(model = "stored-model")))
    }

    @Test
    fun `a blank stored model falls back to the catalogue served by the requested url`() {
        assertEquals("first-model", adapter.resolveLmStudioModel(null, "http://localhost:1234", aiConfig(model = "   ")))

        assertEquals("http://localhost:1234", capturedUrl())
    }

    @Test
    fun `a blank requested url falls back to the stored lmstudio url`() {
        assertEquals("first-model", adapter.resolveLmStudioModel(null, "   ", aiConfig(model = null, lmStudioUrl = "http://localhost:9999")))

        assertEquals("http://localhost:9999", capturedUrl())
    }

    @Test
    fun `a blank stored url as well falls back to the loopback default`() {
        assertEquals("first-model", adapter.resolveLmStudioModel(null, null, aiConfig(model = null, lmStudioUrl = "  ")))

        assertEquals("http://localhost:1234", capturedUrl())
    }

    private fun capturedUrl(): String {
        val captor = argumentCaptor<String>()
        verify(lmStudioService).fetchAvailableModels(captor.capture())
        return captor.firstValue
    }

    private fun aiConfig(
        model: String?,
        lmStudioUrl: String? = null,
    ): AiConfig = AiConfig(model = model, aiProvider = AiProvider.LMSTUDIO.id, lmStudioUrl = lmStudioUrl)
}
