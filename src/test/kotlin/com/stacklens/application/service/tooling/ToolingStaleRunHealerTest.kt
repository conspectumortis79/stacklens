package com.stacklens.application.service.tooling

import com.stacklens.application.port.out.ConfluenceToolPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.port.out.time.ProcessStartPort
import com.stacklens.application.service.StartupHealer
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.model.ConfluenceTool
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

class ToolingStaleRunHealerTest {
    private val toolRepo: ConfluenceToolPort = mock()
    private val i18n: I18nPort = mock()
    private val clock: ClockPort = mock()
    private val processStart: ProcessStartPort = mock()

    private val healer =
        ToolingStaleRunHealer(
            toolRepo = toolRepo,
            i18n = i18n,
            startupHealer = StartupHealer(),
            processStart = processStart,
            clock = clock,
        )

    private fun tool(
        id: Long,
        status: String,
        createdAt: LocalDateTime,
    ): ConfluenceTool =
        ConfluenceTool(
            id = id,
            name = "tool-$id",
            confluencePageUrl = "SCRIPT",
            status = status,
            createdAt = createdAt,
        )

    private fun stubBoot(
        processStartedAt: LocalDateTime,
        now: LocalDateTime,
    ) {
        whenever(processStart.startedAt()).thenReturn(processStartedAt)
        whenever(clock.localDateTime()).thenReturn(now)
        whenever(i18n.t("tooling.be_aborted_restart")).thenReturn("abgebrochen")
    }

    @Test
    fun `a run left over from the previous process is cancelled and saved`() {
        val orphan = tool(1L, AnalysisStatus.GENERATING, LocalDateTime.of(2020, 1, 1, 8, 0))
        val boot = LocalDateTime.of(2020, 1, 1, 9, 0)
        stubBoot(boot, boot.plusSeconds(1))
        whenever(toolRepo.findAllByOrderByCreatedAtDesc()).thenReturn(listOf(orphan))
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { it.arguments[0] }

        healer.healStaleTools()

        assertEquals(AnalysisStatus.CANCELLED, orphan.status)
        assertEquals("abgebrochen", orphan.errorMessage)
        assertNotNull(orphan.completedAt)
        verify(toolRepo).saveConfluenceTool(orphan)
    }

    @Test
    fun `a tool created by the running process keeps its status`() {
        val boot = LocalDateTime.of(2020, 1, 1, 9, 0)
        val justCreated = tool(78L, AnalysisStatus.PENDING, boot.plusSeconds(18))
        val running = tool(79L, AnalysisStatus.GENERATING, boot.plusSeconds(20))
        stubBoot(boot, boot.plusSeconds(30))
        whenever(toolRepo.findAllByOrderByCreatedAtDesc()).thenReturn(listOf(running, justCreated))

        healer.healStaleTools()

        assertEquals(AnalysisStatus.PENDING, justCreated.status)
        assertEquals(AnalysisStatus.GENERATING, running.status)
        assertNull(justCreated.errorMessage)
        assertNull(running.errorMessage)
        verify(toolRepo, never()).saveConfluenceTool(any())
    }

    @Test
    fun `finished tools are never touched`() {
        val boot = LocalDateTime.of(2020, 1, 1, 9, 0)
        val ready = tool(1L, AnalysisStatus.READY, boot.minusHours(3))
        val failed = tool(2L, AnalysisStatus.FAILED, boot.minusHours(2))
        val cancelled = tool(3L, AnalysisStatus.CANCELLED, boot.minusHours(1))
        stubBoot(boot, boot.plusSeconds(5))
        whenever(toolRepo.findAllByOrderByCreatedAtDesc()).thenReturn(listOf(cancelled, failed, ready))

        healer.healStaleTools()

        assertEquals(AnalysisStatus.READY, ready.status)
        assertEquals(AnalysisStatus.FAILED, failed.status)
        assertEquals(AnalysisStatus.CANCELLED, cancelled.status)
        verify(toolRepo, never()).saveConfluenceTool(any())
    }

    @Test
    fun `every in-flight status of the previous process is healed`() {
        val boot = LocalDateTime.of(2020, 1, 1, 9, 0)
        val statuses =
            listOf(
                AnalysisStatus.PENDING,
                AnalysisStatus.ANALYZING,
                AnalysisStatus.GENERATING,
                AnalysisStatus.BUILDING,
                AnalysisStatus.RUNNING,
            )
        val orphans = statuses.mapIndexed { index, status -> tool(index.toLong() + 1, status, boot.minusMinutes(1)) }
        stubBoot(boot, boot.plusSeconds(2))
        whenever(toolRepo.findAllByOrderByCreatedAtDesc()).thenReturn(orphans)
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { it.arguments[0] }

        healer.healStaleTools()

        assertEquals(statuses.size, orphans.size)
        orphans.forEach { assertEquals(AnalysisStatus.CANCELLED, it.status) }
    }

    @Test
    fun `a failing save does not break the heal of the remaining tools`() {
        val boot = LocalDateTime.of(2020, 1, 1, 9, 0)
        val first = tool(1L, AnalysisStatus.BUILDING, boot.minusMinutes(2))
        val second = tool(2L, AnalysisStatus.GENERATING, boot.minusMinutes(1))
        stubBoot(boot, boot.plusSeconds(3))
        whenever(toolRepo.findAllByOrderByCreatedAtDesc()).thenReturn(listOf(first, second))
        whenever(toolRepo.saveConfluenceTool(first)).thenThrow(RuntimeException("db down"))
        whenever(toolRepo.saveConfluenceTool(second)).thenAnswer { it.arguments[0] }

        healer.healStaleTools()

        assertEquals(AnalysisStatus.CANCELLED, second.status)
        verify(toolRepo).saveConfluenceTool(second)
    }

    @Test
    fun `a failing repository lookup is swallowed`() {
        whenever(processStart.startedAt()).thenReturn(LocalDateTime.of(2020, 1, 1, 9, 0))
        whenever(toolRepo.findAllByOrderByCreatedAtDesc()).thenThrow(RuntimeException("db down"))

        healer.healStaleTools()

        verify(toolRepo, never()).saveConfluenceTool(any())
    }
}
