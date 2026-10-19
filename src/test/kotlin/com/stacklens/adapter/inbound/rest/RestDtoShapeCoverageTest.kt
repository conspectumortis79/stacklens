package com.stacklens.adapter.inbound.rest
import com.stacklens.adapter.inbound.rest.config.ConfigExportDtoMapper
import com.stacklens.adapter.out.async.BackgroundStartupTaskExecutor
import com.stacklens.adapter.out.demo.DemoHostResolver
import com.stacklens.application.port.inbound.JiraTicketSummaryUseCase
import com.stacklens.application.port.inbound.code.CodeQaAnalysisUseCase
import com.stacklens.application.port.inbound.config.ConfigExportSnapshot
import com.stacklens.application.port.inbound.config.DeveloperOptionSettings
import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.model.AiConfig
import com.stacklens.domain.model.CodeQaAnalysis
import com.stacklens.domain.model.JiraTicketSummary
import com.stacklens.testsupport.FakeI18n
import com.stacklens.testsupport.PrivateInvocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

class RestDtoShapeCoverageTest {
    @Test
    fun `a summary without a completion time is exported as empty`() {
        val useCase = mock<JiraTicketSummaryUseCase>()
        val running = mock<JiraTicketSummary>()
        whenever(running.completedAt).thenReturn(null)
        whenever(running.createdAt).thenReturn(LocalDateTime.of(2025, 1, 2, 3, 4))
        whenever(useCase.getAllSummaries()).thenReturn(listOf(running))
        val controller = JiraTicketSummaryController(useCase, FakeI18n())

        val listed = controller.list().single()

        assertNull(listed["completedAt"])
        assertEquals("2025-01-02T03:04", listed["createdAt"])

        whenever(running.completedAt).thenReturn(LocalDateTime.of(2025, 1, 2, 5, 6))
        assertEquals("2025-01-02T05:06", controller.list().single()["completedAt"])
        whenever(running.completedAt).thenReturn(null)

        whenever(running.completedAt).thenReturn(LocalDateTime.of(2025, 1, 2, 4, 4))
        val full = PrivateInvocation.invoke(controller, "toFullDto", running) as Map<*, *>

        assertEquals("2025-01-02T04:04", full["completedAt"])
    }

    @Test
    fun `an ai model list with holes is exported without them`() {
        val mapper = ConfigExportDtoMapper()
        val snapshot =
            ConfigExportSnapshot(
                exportedAt = LocalDateTime.of(2025, 1, 1, 0, 0),
                gitlab = emptyList(),
                databases = emptyList(),
                confluence = emptyList(),
                grafana = emptyList(),
                jira = emptyList(),
                github = emptyList(),
                mcp = emptyList(),
                ai = AiConfig(availableModels = "one,,  ,two"),
                language = "de",
                theme = "light",
                developerOptions = DeveloperOptionSettings(false, false, false, false, false),
                uiPreferences = emptyMap(),
            )

        assertEquals(listOf("one", "two"), mapper.toDto(snapshot).ai!!.availableModels)

        val withoutModels = snapshot.copy(ai = AiConfig(availableModels = null))

        assertEquals(emptyList<String>(), mapper.toDto(withoutModels).ai!!.availableModels)
    }

    @Test
    fun `a url without a scheme or host has no demo host`() {
        val resolver = DemoHostResolver()

        assertEquals("db.example", PrivateInvocation.invoke(resolver, "extractHost", "http://db.example:5432/d"))
        assertNull(PrivateInvocation.invoke(resolver, "extractHost", "/just/a/path"))
        assertNull(PrivateInvocation.invoke(resolver, "extractHost", "mailto:someone@example.com"))
        assertNull(PrivateInvocation.invoke(resolver, "extractHost", ":::not a uri:::"))
    }

    @Test
    fun `an integration test that already finished is replayed instead of streamed`() {
        val analysisService = mock<CodeQaAnalysisUseCase>()
        val controller = CodeQaAnalysisApiController(analysisService, FakeI18n() as I18nUseCase)

        for (status in listOf(AnalysisStatus.COMPLETED, AnalysisStatus.FAILED, AnalysisStatus.CANCELLED)) {
            val analysis = mock<CodeQaAnalysis>()
            whenever(analysis.integrationTestStatus).thenReturn(status)
            whenever(analysis.integrationTestLog).thenReturn("log of $status")
            whenever(analysisService.getAnalysis(any())).thenReturn(analysis)

            assertNotNull(controller.integrationTestStream(1L))
        }

        val idle = mock<CodeQaAnalysis>()
        whenever(idle.integrationTestStatus).thenReturn(null)
        whenever(analysisService.getAnalysis(any())).thenReturn(idle)
        assertNotNull(controller.integrationTestStream(1L))

        whenever(analysisService.getAnalysis(any())).thenReturn(null)
        assertNotNull(controller.integrationTestStream(1L))
    }

    @Test
    fun `an uncaught failure is handed to the running thread when it has no handler`() {
        val executor =
            com.stacklens.adapter.out.async
                .BackgroundStartupTaskExecutor()
        val seen = java.util.concurrent.CountDownLatch(2)

        executor.submit {
            seen.countDown()
            throw IllegalStateException("with a handler")
        }
        Thread.sleep(150)
        executor.submit {
            Thread.currentThread().uncaughtExceptionHandler = null
            seen.countDown()
            throw IllegalStateException("without a handler")
        }

        assertTrue(seen.await(5, java.util.concurrent.TimeUnit.SECONDS))
    }
}
