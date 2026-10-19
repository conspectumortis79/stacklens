package com.stacklens.adapter.out.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CopilotOutputSanitizerTest {
    @Test
    fun `isQuotaError returns true for HTTP 402 status`() {
        assertTrue(CopilotOutputSanitizer.isQuotaError("HTTP 402 returned"))
    }

    @Test
    fun `isQuotaError returns true for Payment Required text`() {
        assertTrue(CopilotOutputSanitizer.isQuotaError("Error: Payment Required"))
    }

    @Test
    fun `isQuotaError returns true for quota exceeded`() {
        assertTrue(CopilotOutputSanitizer.isQuotaError("You have quota exceeded your plan"))
    }

    @Test
    fun `isQuotaError returns true for rate limit`() {
        assertTrue(CopilotOutputSanitizer.isQuotaError("API rate limit exceeded"))
    }

    @Test
    fun `isQuotaError returns true for Spending limit`() {
        assertTrue(CopilotOutputSanitizer.isQuotaError("Your Spending limit has been reached"))
    }

    @Test
    fun `isQuotaError is case-insensitive`() {
        assertTrue(CopilotOutputSanitizer.isQuotaError("QUOTA EXCEEDED"))
        assertTrue(CopilotOutputSanitizer.isQuotaError("RATE LIMIT"))
    }

    @Test
    fun `isQuotaError returns false for normal output`() {
        assertFalse(CopilotOutputSanitizer.isQuotaError("Here is your answer."))
    }

    @Test
    fun `isNotInstalledError returns true when cannot find Copilot CLI`() {
        assertTrue(CopilotOutputSanitizer.isNotInstalledError("Cannot find GitHub Copilot CLI binary"))
    }

    @Test
    fun `isNotInstalledError returns true when Install Copilot CLI`() {
        assertTrue(CopilotOutputSanitizer.isNotInstalledError("Install GitHub Copilot CLI to enable this feature"))
    }

    @Test
    fun `isNotInstalledError is case-insensitive`() {
        assertTrue(CopilotOutputSanitizer.isNotInstalledError("INSTALL GITHUB COPILOT CLI"))
    }

    @Test
    fun `isNotInstalledError returns false for normal output`() {
        assertFalse(CopilotOutputSanitizer.isNotInstalledError("Analysis complete."))
    }

    @Test
    fun `scrubSensitiveData redacts ghp_ tokens`() {
        val result = CopilotOutputSanitizer.scrubSensitiveData("token=ghp_abcdefghijklmnopqrstuvwxyz1234")
        assertTrue(result.contains("[REDACTED_PAT]"))
        assertFalse(result.contains("ghp_abcdefghijklmnopqrstuvwxyz1234"))
    }

    @Test
    fun `scrubSensitiveData redacts gho_ tokens`() {
        val result = CopilotOutputSanitizer.scrubSensitiveData("gho_abcdefghijklmnopqrstuvwxyz1234567")
        assertTrue(result.contains("[REDACTED_PAT]"))
    }

    @Test
    fun `scrubSensitiveData redacts ghs_ tokens`() {
        val result = CopilotOutputSanitizer.scrubSensitiveData("ghs_abcdefghijklmnopqrstuvwxyz1234567")
        assertTrue(result.contains("[REDACTED_PAT]"))
    }

    @Test
    fun `scrubSensitiveData redacts ghu_ tokens`() {
        val result = CopilotOutputSanitizer.scrubSensitiveData("ghu_abcdefghijklmnopqrstuvwxyz1234567")
        assertTrue(result.contains("[REDACTED_PAT]"))
    }

    @Test
    fun `scrubSensitiveData redacts ghr_ tokens`() {
        val result = CopilotOutputSanitizer.scrubSensitiveData("ghr_abcdefghijklmnopqrstuvwxyz1234567")
        assertTrue(result.contains("[REDACTED_PAT]"))
    }

    @Test
    fun `scrubSensitiveData does not redact short strings that look like tokens`() {
        val result = CopilotOutputSanitizer.scrubSensitiveData("ghp_short")
        assertFalse(result.contains("[REDACTED_PAT]"))
        assertTrue(result.contains("ghp_short"))
    }

    @Test
    fun `scrubSensitiveData redacts Authorization Bearer headers`() {
        val result = CopilotOutputSanitizer.scrubSensitiveData("Authorization: Bearer abc123def456ghi789")
        assertTrue(result.contains("Authorization: Bearer [REDACTED]"))
        assertFalse(result.contains("abc123def456ghi789"))
    }

    @Test
    fun `scrubSensitiveData redacts Authorization Bearer headers case-insensitively`() {
        val result = CopilotOutputSanitizer.scrubSensitiveData("authorization: bearer abc123def456ghi789")
        assertTrue(result.contains("[REDACTED]"))
    }

    @Test
    fun `scrubSensitiveData redacts GH_TOKEN environment assignments`() {
        val result = CopilotOutputSanitizer.scrubSensitiveData("GH_TOKEN=ghp_abcdefghijklmnopqrstuvwxyz1234")
        assertTrue(result.contains("GH_TOKEN=[REDACTED]"))
    }

    @Test
    fun `scrubSensitiveData redacts COPILOT_GITHUB_TOKEN environment assignments`() {
        val result = CopilotOutputSanitizer.scrubSensitiveData("COPILOT_GITHUB_TOKEN=ghp_abcdefghijklmnopqrstuvwxyz1234")
        assertTrue(result.contains("COPILOT_GITHUB_TOKEN=[REDACTED]"))
    }

    @Test
    fun `scrubSensitiveData passes normal text unchanged`() {
        val normal = "Just some regular log output without any secrets."
        assertEquals(normal, CopilotOutputSanitizer.scrubSensitiveData(normal))
    }

    @Test
    fun `stripSystemNotifications removes system_notification blocks`() {
        val text = "Hello\n<system_notification>secret stuff</system_notification>\nWorld"
        val result = CopilotOutputSanitizer.stripSystemNotifications(text)
        assertFalse(result.contains("system_notification"))
        assertFalse(result.contains("secret stuff"))
        assertTrue(result.contains("Hello"))
        assertTrue(result.contains("World"))
    }

    @Test
    fun `stripSystemNotifications is case-insensitive`() {
        val text = "Hello\n<SYSTEM_NOTIFICATION>secret</SYSTEM_NOTIFICATION>\nWorld"
        val result = CopilotOutputSanitizer.stripSystemNotifications(text)
        assertFalse(result.contains("secret"))
    }

    @Test
    fun `stripSystemNotifications handles multi-line blocks`() {
        val text = "Before\n<system_notification>\nLine 1\nLine 2\n</system_notification>\nAfter"
        val result = CopilotOutputSanitizer.stripSystemNotifications(text)
        assertFalse(result.contains("Line 1"))
        assertFalse(result.contains("Line 2"))
    }

    @Test
    fun `stripSystemNotifications collapses 3+ blank lines to 2`() {
        val text = "Line1\n\n\n\n\nLine2"
        val result = CopilotOutputSanitizer.stripSystemNotifications(text)
        assertFalse(result.contains("\n\n\n"))
        assertTrue(result.contains("Line1"))
        assertTrue(result.contains("Line2"))
    }

    @Test
    fun `stripSystemNotifications trims result`() {
        val text = "  \n  Hello  \n  "
        val result = CopilotOutputSanitizer.stripSystemNotifications(text)
        assertEquals("Hello", result)
    }

    @Test
    fun `stripSystemNotifications handles text without notifications`() {
        val text = "Just plain text with no notifications."
        val result = CopilotOutputSanitizer.stripSystemNotifications(text)
        assertEquals("Just plain text with no notifications.", result)
    }
}
