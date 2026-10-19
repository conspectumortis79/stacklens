package com.stacklens.application.service.loadtest
import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.adapter.out.loadtest.K6ScriptTransformerAdapter
import com.stacklens.application.port.inbound.loadtest.K6StatusResult
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.process.ProcessRunPort
import com.stacklens.application.service.loadtest.LoadTestAi
import com.stacklens.testsupport.FakeExecutableLookupPort
import com.stacklens.testsupport.FakeHostDirectoryPort
import com.stacklens.testsupport.FakeShellPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class K6ProcessControllerSmokeTest {
    private val shellProbe = FakeShellPort()
    private val hostDirs = FakeHostDirectoryPort()

    private val analysisRepo = mock<com.stacklens.application.port.out.LoadTestAnalysisPort>()
    private val aiService = mock<com.stacklens.application.service.loadtest.LoadTestAi>()
    private val i18n: I18nPort = mock()

    @Test
    fun `K6ProcessController can be constructed with all dependencies`() {
        val ctl =
            testK6ProcessController(
                analysisRepo = analysisRepo,
                aiService = aiService,
                i18n = i18n,
                processRunPort = mock<ProcessRunPort>(),
                localBaseUrl = mockLocalBaseUrl(),
                scriptTransformer = K6ScriptTransformerAdapter(mockLocalBaseUrl()),
                executables = FakeExecutableLookupPort(),
                fileSystem = JavaFileSystemAdapter(),
            )
        assertNotNull(ctl)
    }

    @Test
    fun `K6ProcessController resolveToolBin returns the first executable candidate`() {
        val ctl =
            testK6ProcessController(
                analysisRepo = analysisRepo,
                aiService = aiService,
                i18n = i18n,
                processRunPort = mock<ProcessRunPort>(),
                localBaseUrl = mockLocalBaseUrl(),
                scriptTransformer = K6ScriptTransformerAdapter(mockLocalBaseUrl()),
                executables = FakeExecutableLookupPort(resolved = mapOf("k6" to "/usr/bin/k6")),
                fileSystem = JavaFileSystemAdapter(),
            )
        val result = ctl.resolveToolBin("k6", listOf("/usr/bin/k6", "/usr/local/bin/k6"))
        assertEquals("/usr/bin/k6", result)
    }

    @Test
    fun `K6ProcessController resolveToolBin returns null when no candidate is executable`() {
        val ctl =
            testK6ProcessController(
                analysisRepo = analysisRepo,
                aiService = aiService,
                i18n = i18n,
                processRunPort = mock<ProcessRunPort>(),
                localBaseUrl = mockLocalBaseUrl(),
                scriptTransformer = K6ScriptTransformerAdapter(mockLocalBaseUrl()),
                executables = FakeExecutableLookupPort(),
                fileSystem = JavaFileSystemAdapter(),
            )
        val result = ctl.resolveToolBin("k6", listOf("/nonexistent/k6", "/also/nonexistent/k6"))
        assertEquals(null, result)
    }

    private fun mockLocalBaseUrl(): com.stacklens.application.port.out.loadtest.LocalBaseUrlPort {
        val mock = mock<com.stacklens.application.port.out.loadtest.LocalBaseUrlPort>()
        org.mockito.Mockito
            .doReturn("http://localhost:8090")
            .`when`(mock)
            .currentBaseUrl()
        return mock
    }

    @Test
    fun `K6StatusResult running flag is true when constructed with running true`() {
        val result =
            K6StatusResult(
                running = true,
                exitCode = null,
                output = "",
                error = "",
            )
        assertTrue(result.running)
    }

    @Test
    fun `K6StatusResult running flag is false when constructed with running false`() {
        val result =
            K6StatusResult(
                running = false,
                exitCode = 0,
                output = "ok",
                error = "",
            )
        assertEquals(false, result.running)
        assertEquals(0, result.exitCode)
    }
}
