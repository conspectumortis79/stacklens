package com.stacklens.application.service

import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.process.AgentProcessPort
import com.stacklens.application.port.out.textfilter.OutputFilterPort
import com.stacklens.domain.text.filter.FilterResult
import com.stacklens.testsupport.PrivateInvocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class IvanStageRunnerFilterOutputCoverageTest {
    private val outputFilter = mock<OutputFilterPort>()

    @Test
    fun `a disabled output filter keeps the raw subprocess output`() {
        whenever(outputFilter.enabled).thenReturn(false)

        assertEquals("raw", filter(runner(outputFilter), "raw"))
    }

    @Test
    fun `an enabled filter is not applied to an empty output`() {
        whenever(outputFilter.enabled).thenReturn(true)

        assertEquals("", filter(runner(outputFilter), ""))
    }

    @Test
    fun `an enabled filter rewrites a non empty output`() {
        whenever(outputFilter.enabled).thenReturn(true)
        whenever(outputFilter.filter(any(), any(), any())).thenReturn(FilterResult(text = "filtered", originalChars = 3, filteredChars = 8, specName = "make"))

        assertEquals("filtered", filter(runner(outputFilter), "raw"))
    }

    private fun runner(filter: OutputFilterPort): IvanStageRunner = IvanStageRunner(mock<AgentProcessPort>(), mock<I18nPort>(), JavaFileSystemAdapter(), filter)

    private fun filter(
        runner: IvanStageRunner,
        rawOutput: String,
    ): Any? = PrivateInvocation.invoke(runner, "filterSubprocessOutput", listOf("make", "coverage"), rawOutput)
}
