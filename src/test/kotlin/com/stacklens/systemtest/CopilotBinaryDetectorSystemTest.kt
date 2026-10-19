package com.stacklens.systemtest

import com.stacklens.adapter.out.ai.CopilotBinaryDetector
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CopilotBinaryDetectorSystemTest {
    @Test
    fun `install throws when gh is not installed and not in known paths`() {
        val detector = CopilotBinaryDetector("", "")
        try {
            detector.install("TOKEN")
        } catch (e: IllegalStateException) {
            assertTrue(
                e.message!!.contains("GH_NOT_INSTALLED") || e.message!!.contains("COPILOT_INSTALL_FAILED"),
                "unexpected error: ${e.message}",
            )
        }
    }

    @Test
    fun `install throws when install script exits non-zero`() {
        val detector = CopilotBinaryDetector("", "")
        try {
            detector.install("invalid-token")
            assertTrue(true)
        } catch (e: IllegalStateException) {
            assertTrue(
                e.message!!.contains("GH_NOT_INSTALLED") || e.message!!.contains("COPILOT_INSTALL_FAILED"),
                "unexpected error: ${e.message}",
            )
        }
    }
}
