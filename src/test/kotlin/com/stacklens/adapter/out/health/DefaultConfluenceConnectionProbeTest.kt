package com.stacklens.adapter.out.health

import com.stacklens.application.port.model.ConnectionFailureKind
import com.stacklens.application.port.out.ConfluenceConnectionTestPort
import com.stacklens.application.port.out.health.ConnectionProbeResult
import com.stacklens.domain.model.ConfluenceConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DefaultConfluenceConnectionProbeTest {
    private val delegate = mock<ConfluenceConnectionTestPort>()
    private val probe = DefaultConfluenceConnectionProbe(delegate)
    private val cfg = ConfluenceConfig(id = 1L, name = "c", baseUrl = "https://wiki.example.com", username = "u", apiToken = "t")

    @Test
    fun `probe returns Ok when delegate returns Success`() {
        whenever(delegate.testConnection(cfg, null))
            .thenReturn(ConfluenceConnectionTestPort.TestResult.Success(emptyMap()))
        val r = probe.probe(cfg)
        assertTrue(r is ConnectionProbeResult.Ok)
    }

    @Test
    fun `probe returns Failure with the message from the delegate payload when delegate returns Failure`() {
        whenever(delegate.testConnection(cfg, null))
            .thenReturn(
                ConfluenceConnectionTestPort.TestResult.Failure(
                    kind = ConnectionFailureKind.OTHER,
                    payload = mapOf("message" to "boom"),
                ),
            )
        val r = probe.probe(cfg)
        assertTrue(r is ConnectionProbeResult.Failure)
        r as ConnectionProbeResult.Failure
        assertEquals("boom", r.errorMessage)
    }

    @Test
    fun `probe returns empty error message when payload has no message field`() {
        whenever(delegate.testConnection(cfg, null))
            .thenReturn(ConfluenceConnectionTestPort.TestResult.Failure(kind = ConnectionFailureKind.OTHER, payload = emptyMap()))
        val r = probe.probe(cfg)
        assertTrue(r is ConnectionProbeResult.Failure)
        r as ConnectionProbeResult.Failure
        assertEquals("", r.errorMessage)
    }

    @Test
    fun `probe truncates long error messages to MAX_ERROR_MESSAGE_LEN`() {
        val longMsg = "x".repeat(5_000)
        whenever(delegate.testConnection(cfg, null))
            .thenReturn(
                ConfluenceConnectionTestPort.TestResult.Failure(
                    kind = ConnectionFailureKind.OTHER,
                    payload = mapOf("message" to longMsg),
                ),
            )
        val r = probe.probe(cfg)
        assertTrue(r is ConnectionProbeResult.Failure)
        r as ConnectionProbeResult.Failure
        assertTrue(r.errorMessage.length <= 500, "message was ${r.errorMessage.length} chars")
        assertTrue(r.errorMessage.endsWith("…"))
    }

    @Test
    fun `probe collapses newlines and carriage returns to spaces`() {
        whenever(delegate.testConnection(cfg, null))
            .thenReturn(
                ConfluenceConnectionTestPort.TestResult.Failure(
                    kind = ConnectionFailureKind.OTHER,
                    payload = mapOf("message" to "line1\r\nline2"),
                ),
            )
        val r = probe.probe(cfg)
        assertTrue(r is ConnectionProbeResult.Failure)
        r as ConnectionProbeResult.Failure
        assertEquals("line1  line2", r.errorMessage)
    }
}
