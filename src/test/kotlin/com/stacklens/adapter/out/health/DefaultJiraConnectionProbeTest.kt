package com.stacklens.adapter.out.health

import com.stacklens.application.port.model.ConnectionFailureKind
import com.stacklens.application.port.out.JiraConnectionTestPort
import com.stacklens.application.port.out.health.ConnectionProbeResult
import com.stacklens.domain.model.JiraConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DefaultJiraConnectionProbeTest {
    private val delegate = mock<JiraConnectionTestPort>()
    private val probe = DefaultJiraConnectionProbe(delegate)
    private val cfg =
        JiraConfig(
            id = 1L,
            name = "j",
            baseUrl = "https://example.atlassian.net",
            apiToken = "tok",
        )

    @Test
    fun `probe returns Ok when delegate returns Success`() {
        whenever(delegate.testConnection(cfg, null))
            .thenReturn(JiraConnectionTestPort.TestResult.Success(emptyMap()))
        val r = probe.probe(cfg)
        assertTrue(r is ConnectionProbeResult.Ok)
    }

    @Test
    fun `probe returns Failure with the message from the delegate payload when delegate returns Failure`() {
        whenever(delegate.testConnection(cfg, null))
            .thenReturn(
                JiraConnectionTestPort.TestResult.Failure(
                    kind = ConnectionFailureKind.OTHER,
                    payload = mapOf("message" to "bad token"),
                ),
            )
        val r = probe.probe(cfg)
        assertTrue(r is ConnectionProbeResult.Failure)
        r as ConnectionProbeResult.Failure
        assertEquals("bad token", r.errorMessage)
    }

    @Test
    fun `probe returns empty error message when payload has no message field`() {
        whenever(delegate.testConnection(cfg, null))
            .thenReturn(JiraConnectionTestPort.TestResult.Failure(kind = ConnectionFailureKind.OTHER, payload = emptyMap()))
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
                JiraConnectionTestPort.TestResult.Failure(
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
                JiraConnectionTestPort.TestResult.Failure(
                    kind = ConnectionFailureKind.OTHER,
                    payload = mapOf("message" to "line1\r\nline2"),
                ),
            )
        val r = probe.probe(cfg)
        assertTrue(r is ConnectionProbeResult.Failure)
        r as ConnectionProbeResult.Failure
        assertEquals("line1  line2", r.errorMessage)
    }

    @Test
    fun `probe handles a non-string message payload field by toString-ing it`() {
        whenever(delegate.testConnection(cfg, null))
            .thenReturn(
                JiraConnectionTestPort.TestResult.Failure(
                    kind = ConnectionFailureKind.OTHER,
                    payload = mapOf("message" to 42),
                ),
            )
        val r = probe.probe(cfg)
        assertTrue(r is ConnectionProbeResult.Failure)
        r as ConnectionProbeResult.Failure
        assertEquals("42", r.errorMessage)
    }
}
