package com.stacklens.adapter.out.ai

import com.stacklens.application.port.model.ConnectionFailureKind
import com.stacklens.application.port.out.ai.AiConnectionTestPort
import com.stacklens.application.port.out.ai.ProviderSpec
import com.stacklens.application.port.out.platform.ContainerRuntime
import com.stacklens.application.service.FakeI18n
import com.stacklens.testsupport.FakeContainerRuntimeProbePort
import com.stacklens.testsupport.FakeLmStudioModelLookupPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LmStudioConnectionTesterNullCauseCoverageTest {
    private val i18n = FakeI18n()

    @Test
    fun `an empty model catalogue is read as no models at all`() {
        val tester =
            LmStudioConnectionTester(
                i18n = i18n,
                containerRuntimeProbe = FakeContainerRuntimeProbePort(ContainerRuntime.NONE),
                lmStudioService = FakeLmStudioModelLookupPort(),
            )

        val result = tester.test(ProviderSpec.LmStudio(baseUrl = "http://localhost:1234"))

        assertEquals(
            AiConnectionTestPort.TestResult.Success(
                mapOf("success" to true, "modelCount" to 0, "models" to emptyList<String>()),
            ),
            result,
        )
    }

    @Test
    fun `an lmstudio failure without a cause message still renders the localized error`() {
        val tester =
            LmStudioConnectionTester(
                i18n = i18n,
                containerRuntimeProbe = FakeContainerRuntimeProbePort(ContainerRuntime.NONE),
                lmStudioService = FakeLmStudioModelLookupPort { throw IllegalStateException() },
            )

        val result = tester.test(ProviderSpec.LmStudio(baseUrl = "http://localhost:1234"))

        assertEquals(
            AiConnectionTestPort.TestResult.Failure(
                ConnectionFailureKind.CONNECTION,
                mapOf("success" to false, "error" to "config.be_lmstudio_unreachable()"),
            ),
            result,
        )
    }
}
