package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.search.GrafanaSearchPort
import com.stacklens.domain.enums.SourceType
import com.stacklens.domain.model.SearchResult
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future

class McpAwareGrafanaScanCollectionTest {
    private val adapter =
        McpAwareGrafanaSearchAdapter(
            mock<McpServerConfigPort>(),
            mock<McpGatewayPort>(),
            ObjectMapper(),
            mock<GrafanaSearchPort>(),
        )

    @AfterEach
    fun clearInterruptFlag() {
        Thread.interrupted()
    }

    private fun result(name: String): SearchResult =
        SearchResult(
            sessionId = 7L,
            keyword = "kw",
            sourceType = SourceType.GRAFANA,
            repoName = name,
            filePath = "$name/dashboard",
        )

    @Test
    fun `completed futures are collected in submission order`() {
        val first = mock<Future<List<SearchResult>>>()
        whenever(first.get()).thenReturn(listOf(result("a"), result("a")))
        val second = mock<Future<List<SearchResult>>>()
        whenever(second.get()).thenReturn(listOf(result("b")))

        val collected = adapter.collectScannedDashboards(listOf(first, second), "kw")

        assertEquals(listOf("a", "a", "b"), collected.map { it.repoName })
    }

    @Test
    fun `an interrupted wait keeps the interrupt flag set`() {
        val interrupted = mock<Future<List<SearchResult>>>()
        whenever(interrupted.get()).thenThrow(InterruptedException("wait interrupted"))

        val collected = adapter.collectScannedDashboards(listOf(interrupted), "kw")

        assertTrue(collected.isEmpty())
        assertTrue(Thread.currentThread().isInterrupted)
    }

    @Test
    fun `a failed dashboard scan is skipped instead of aborting the search`() {
        val broken = mock<Future<List<SearchResult>>>()
        whenever(broken.get()).thenThrow(ExecutionException(RuntimeException("grafana_search_dashboards: unavailable")))
        val done = mock<Future<List<SearchResult>>>()
        whenever(done.get()).thenReturn(listOf(result("kept")))

        val collected = adapter.collectScannedDashboards(listOf(broken, done), "kw")

        assertEquals(listOf("kept"), collected.map { it.repoName })
    }
}
