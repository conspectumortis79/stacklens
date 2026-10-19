package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.ticket.JiraTicketImplementationUseCase
import com.stacklens.domain.model.JiraTicketImplementation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class JiraTicketImplementationControllerTest {
    @Mock
    private lateinit var analysisService: JiraTicketImplementationUseCase

    private val controller by lazy { JiraTicketImplementationController(analysisService) }

    private fun mockAnalysis(
        id: Long = 1L,
        key: String = "PROJ-1",
        status: String = "COMPLETED",
    ) = JiraTicketImplementation(id = id, ticketKey = key, status = status, createdAt = LocalDateTime.now())

    @Test
    fun listReturnsSummaryMaps() {
        whenever(analysisService.getAllAnalyses()).thenReturn(
            listOf(mockAnalysis(1L, "PROJ-1", "COMPLETED"), mockAnalysis(2L, "PROJ-2", "FAILED")),
        )
        val result = controller.list()
        assertEquals(2, result.size)
        assertEquals("PROJ-1", result[0]["ticketKey"])
        assertEquals("COMPLETED", result[0]["status"])
        assertEquals("PROJ-2", result[1]["ticketKey"])
    }

    @Test
    fun deleteReturns204() {
        whenever(analysisService.findAnalysisById(42L)).thenReturn(mockAnalysis(id = 42L))
        val resp = controller.delete(42L)
        assertEquals(204, resp.statusCode.value())
    }

    @Test
    fun deleteAllReturnsDeletedTrue() {
        val resp = controller.deleteAll()
        assertEquals(200, resp.statusCode.value())
        assertEquals(true, resp.body?.get("deleted"))
    }

    @Test
    fun `list returns completedAt as string when completedAt is set`() {
        val completedAt = LocalDateTime.of(2024, 3, 1, 12, 0, 0)
        val analysis =
            mockAnalysis(id = 5L, key = "PROJ-5", status = "COMPLETED").also {
                it.completedAt = completedAt
            }
        whenever(analysisService.getAllAnalyses()).thenReturn(listOf(analysis))
        val result = controller.list()
        assertEquals(1, result.size)
        assertEquals(completedAt.toString(), result[0]["completedAt"])
    }

    @Test
    fun `list returns empty list when no analyses`() {
        whenever(analysisService.getAllAnalyses()).thenReturn(emptyList())
        val result = controller.list()
        assertEquals(0, result.size)
    }

    @Test
    fun `delete returns 404 when id not found`() {
        whenever(analysisService.findAnalysisById(99L)).thenReturn(null)
        val resp = controller.delete(99L)
        assertEquals(404, resp.statusCode.value())
    }

    @Test
    fun `delete returns 204 when id is in second position (any predicate covers both branches)`() {
        whenever(analysisService.findAnalysisById(2L)).thenReturn(mockAnalysis(id = 2L))
        val resp = controller.delete(2L)
        assertEquals(204, resp.statusCode.value())
    }

    @Test
    fun `get returns 404 when id not found`() {
        whenever(analysisService.findAnalysisById(99L)).thenReturn(null)
        val resp = controller.get(99L)
        assertEquals(404, resp.statusCode.value())
    }

    @Test
    fun `get returns 200 with dto when id found and completedAt is null`() {
        val analysis = mockAnalysis(id = 7L, key = "PROJ-7", status = "PENDING")
        whenever(analysisService.findAnalysisById(7L)).thenReturn(analysis)
        val resp = controller.get(7L)
        assertEquals(200, resp.statusCode.value())
        val body = resp.body!!
        assertEquals(7L, body.id)
        assertEquals("PROJ-7", body.ticketKey)
        assertEquals("PENDING", body.status)
        assertEquals(null, body.completedAt)
        assertEquals(null, body.ticket)
        assertEquals(null, body.codeImplementation)
        assertEquals(null, body.monorepoAnalysis)
    }

    @Test
    fun `get returns 200 with dto including completedAt when set`() {
        val completedAt = LocalDateTime.of(2025, 1, 15, 10, 30, 0)
        val analysis =
            mockAnalysis(id = 8L, key = "PROJ-8", status = "COMPLETED").also {
                it.completedAt = completedAt
                it.ticketJson = """{\"summary\":\"Test\"}"""
                it.codeImplementationJson = """{\"files\":[]}"""
                it.monorepoAnalysisJson = """{\"subPath\":\"api\"}"""
            }
        whenever(analysisService.findAnalysisById(8L)).thenReturn(analysis)
        val resp = controller.get(8L)
        assertEquals(200, resp.statusCode.value())
        val body = resp.body!!
        assertEquals(8L, body.id)
        assertEquals("PROJ-8", body.ticketKey)
        assertEquals("COMPLETED", body.status)
        assertEquals(completedAt.toString(), body.completedAt)
        assertEquals("""{\"summary\":\"Test\"}""", body.ticket)
        assertEquals("""{\"files\":[]}""", body.codeImplementation)
        assertEquals("""{\"subPath\":\"api\"}""", body.monorepoAnalysis)
    }
}
