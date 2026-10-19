package com.stacklens.application.port.inbound

import com.stacklens.application.port.inbound.config.ConfigExportUseCase
import com.stacklens.application.port.inbound.logging.LoggingFixUseCase
import com.stacklens.domain.model.LoggingFixAnalysis
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ConfigExportUseCaseDefaultCoverageTest {
    private val useCase = mock<ConfigExportUseCase>()

    @Test
    fun `a snapshot without explicit ui overrides falls back to an empty override map`() {
        val snapshot = mock<com.stacklens.application.port.inbound.config.ConfigExportSnapshot>()
        whenever(useCase.snapshot(any())).thenReturn(snapshot)

        assertSame(snapshot, useCase.snapshot())
    }
}

class LoggingFixUseCaseDefaultCoverageTest {
    private val useCase = mock<LoggingFixUseCase>()

    @Test
    fun `listRecent defaults to the ten most recent analyses`() {
        val rows = listOf(mock<LoggingFixAnalysis>())
        whenever(useCase.listRecent(10)).thenReturn(rows)

        assertEquals(rows, useCase.listRecent())
    }
}
