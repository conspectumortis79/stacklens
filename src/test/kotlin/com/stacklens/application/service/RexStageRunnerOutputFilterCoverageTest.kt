package com.stacklens.application.service

import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.appconfig.CopilotConfigPort
import com.stacklens.application.port.out.file.FileSystemPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.platform.PlatformPort
import com.stacklens.application.port.out.process.AgentProcessPort
import com.stacklens.application.port.out.textfilter.OutputFilterPort
import com.stacklens.domain.text.filter.FilterResult
import com.stacklens.testsupport.PrivateInvocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RexStageRunnerOutputFilterCoverageTest {
    private val outputFilter = mock<OutputFilterPort>()

    @Test
    fun `a disabled output filter keeps the raw make output`() {
        whenever(outputFilter.enabled).thenReturn(false)

        assertEquals("raw", filter(runner(outputFilter = outputFilter), "raw"))
    }

    @Test
    fun `an enabled filter is not applied to an empty output`() {
        whenever(outputFilter.enabled).thenReturn(true)

        assertEquals("", filter(runner(outputFilter = outputFilter), ""))
    }

    @Test
    fun `an enabled filter rewrites a non empty output`() {
        whenever(outputFilter.enabled).thenReturn(true)
        whenever(outputFilter.filter(any(), any(), any())).thenReturn(FilterResult(text = "filtered", originalChars = 3, filteredChars = 8, specName = "make"))

        assertEquals("filtered", filter(runner(outputFilter = outputFilter), "raw"))
    }

    private fun runner(outputFilter: OutputFilterPort): RexStageRunner {
        val filePort = mock<FileSystemPort>()
        val aiFixer =
            RexAiFixService(
                copilot = mock<CopilotStreamingPort>(),
                copilotConfig = mock<CopilotConfigPort>(),
                processPort = mock<AgentProcessPort>(),
                filePort = filePort,
                loadPrompt = { _, _ -> "prompt" },
                fileAttributes = filePort,
            )
        return RexStageRunner(
            processPort = mock<AgentProcessPort>(),
            i18n = mock<I18nPort>(),
            platformPort = mock<PlatformPort>(),
            aiFixer = aiFixer,
            coverageStep = RexCoverageStep(mock<AgentProcessPort>(), mock<I18nPort>(), aiFixer),
            fileAttributes = filePort,
            outputFilter = outputFilter,
        )
    }

    private fun filter(
        runner: RexStageRunner,
        rawOutput: String,
    ): Any? = PrivateInvocation.invoke(runner, "filterMakeOutput", listOf("make", "coverage"), rawOutput)
}
