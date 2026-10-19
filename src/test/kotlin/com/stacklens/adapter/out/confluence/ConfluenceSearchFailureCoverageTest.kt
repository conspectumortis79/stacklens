package com.stacklens.adapter.out.confluence

import com.stacklens.domain.model.ConfluenceConfig
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ConfluenceSearchFailureCoverageTest {
    private lateinit var server: MockWebServer
    private lateinit var client: ConfluenceClient

    @BeforeEach
    fun startServer() {
        server = MockWebServer()
        server.start()
        client =
            ConfluenceClient(
                readTimeoutMs = 30_000,
            )
    }

    @AfterEach
    fun stopServer() {
        server.shutdown()
    }

    private fun cfg() =
        ConfluenceConfig(
            id = 1L,
            name = "test",
            baseUrl = server.url("/").toString().trimEnd('/'),
            username = "u",
            apiToken = "t",
        )

    @Test
    fun `an interrupted wait restores the flag and answers with no results`() {
        Thread.currentThread().interrupt()

        val results = client.searchKeyword(cfg(), "keyword", emptyList(), 1L, 0)

        assertEquals(0, results.size)
        assertTrue(Thread.currentThread().isInterrupted)
        Thread.interrupted()
    }

    @Test
    fun `a failing search path configuration answers with no results`() {
        val config = mock<ConfluenceConfig>()
        whenever(config.baseUrl).thenReturn(server.url("/").toString().trimEnd('/'))
        whenever(config.name).thenReturn("broken-path")
        whenever(config.username).thenReturn("u")
        whenever(config.apiToken).thenReturn("t")
        whenever(config.cloudMode).thenReturn(true).thenThrow(IllegalStateException("no search path"))

        val results = client.searchKeyword(config, "keyword", emptyList(), 1L, 0)

        assertEquals(0, results.size)
    }
}
