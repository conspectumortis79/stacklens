package com.stacklens.adapter.out.ai

import com.stacklens.application.port.out.ai.AiConnectionTestPort
import com.stacklens.application.port.out.ai.ProviderSpec
import com.stacklens.application.port.out.platform.ContainerRuntime
import com.stacklens.application.service.FakeI18n
import com.stacklens.testsupport.FakeContainerRuntimeProbePort
import com.stacklens.testsupport.FakeLmStudioModelLookupPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LmStudioConnectionTesterTest {
    private val i18n = FakeI18n()

    @Test
    fun `test reads the model ids through the lookup port`() {
        val tester =
            LmStudioConnectionTester(
                i18n = i18n,
                containerRuntimeProbe = FakeContainerRuntimeProbePort(ContainerRuntime.NONE),
                lmStudioService = FakeLmStudioModelLookupPort { listOf("llama-3.1-8b", "qwen2.5") },
            )

        val result = tester.test(ProviderSpec.LmStudio(baseUrl = "http://localhost:1234"))

        assertEquals(
            AiConnectionTestPort.TestResult.Success(
                mapOf("success" to true, "modelCount" to 2, "models" to listOf("llama-3.1-8b", "qwen2.5")),
            ),
            result,
        )
    }

    @Test
    fun `test reports no models when the lookup port has no catalogue`() {
        val tester =
            LmStudioConnectionTester(
                i18n = i18n,
                containerRuntimeProbe = FakeContainerRuntimeProbePort(ContainerRuntime.NONE),
                lmStudioService = FakeLmStudioModelLookupPort(),
            )

        val result = tester.test(ProviderSpec.LmStudio(baseUrl = "http://localhost:1234"))

        assertEquals(
            AiConnectionTestPort.TestResult.Success(mapOf("success" to true, "modelCount" to 0, "models" to emptyList<String>())),
            result,
        )
    }

    @Test
    fun `test fails when the lookup port throws`() {
        val tester =
            LmStudioConnectionTester(
                i18n = i18n,
                containerRuntimeProbe = FakeContainerRuntimeProbePort(ContainerRuntime.NONE),
                lmStudioService = FakeLmStudioModelLookupPort { throw IllegalStateException("down") },
            )

        val result = tester.test(ProviderSpec.LmStudio(baseUrl = "http://localhost:1234"))

        val payload = (result as AiConnectionTestPort.TestResult.Failure).payload
        assertEquals(false, payload["success"])
        assertTrue(payload["error"].toString().startsWith("config.be_lmstudio_unreachable"), "unexpected: ${payload["error"]}")
    }

    @Test
    fun `test resolves the base url for the active container runtime`() {
        val captured = mutableListOf<String>()
        val tester =
            LmStudioConnectionTester(
                i18n = i18n,
                containerRuntimeProbe = FakeContainerRuntimeProbePort(ContainerRuntime.DOCKER),
                lmStudioService =
                    FakeLmStudioModelLookupPort { url ->
                        captured += url
                        listOf("model-x")
                    },
            )

        val result = tester.test(ProviderSpec.LmStudio(baseUrl = "http://localhost:1234"))

        assertEquals(listOf("http://host.docker.internal:1234"), captured)
        assertTrue(result is AiConnectionTestPort.TestResult.Success)
    }
}
