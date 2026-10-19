package com.stacklens.application.service

import com.stacklens.application.collaboration.StartupRecovery
import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.port.out.SqlAnalysisPort
import com.stacklens.application.port.out.ai.AiUsageContextPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.service.SqlAi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SqlAnalysisServiceDelegationTest {
    private val sqlTestRunner = mock<SqlTestRunner>()

    private val service =
        SqlAnalysisService(
            mock<CopilotPort>(),
            mock<AiUsageContextPort>(),
            mock<JsonCodecPort>(),
            mock<SqlAnalysisPort>(),
            mock<SqlAi>(),
            mock<StartupRecovery>(),
            sqlTestRunner,
            recordLifecycle = SqlHistoryService(mock()),
            parallelTasks = com.stacklens.adapter.out.async.ForkJoinParallelTaskPort,
            clock = com.stacklens.adapter.out.time.SystemClockPort,
        )

    @Test
    fun `the setup script split is delegated to the test runner`() {
        whenever(sqlTestRunner.splitSetupSql(any())).thenReturn(listOf("SELECT 1;", "INSERT INTO t VALUES (1);"))

        assertEquals(2, service.splitSetupSql("SELECT 1; INSERT INTO t VALUES (1);").size)
    }

    @Test
    fun `a test run result keeps its optional fields empty by default`() {
        val result = SqlAnalysisService.TestRunResult(columns = listOf("id"), rows = listOf(listOf("1")), rowCount = 1)

        assertFalse(result.truncated)
        assertNull(result.error)
    }
}
