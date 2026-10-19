package com.stacklens.application.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class CopilotAgentPipelineRepoScanningTest : CopilotAgentPipelineServiceTestBase() {
    @Test
    fun `scanRepoStructure returns not-found for missing directory`() {
        val svc = makeService()
        val result = svc.scanRepoStructure("/nonexistent/path/xyz-never-exists")
        assertTrue(
            result.contains("not found") || result.contains("access denied"),
            "Expected 'not found' or 'access denied', got: $result",
        )
    }

    @Test
    fun `scanRepoStructure returns not-a-directory for file path`() {
        val svc = makeService()
        val tempFile = Files.createTempFile("scantest", ".txt").toFile()
        try {
            val result = svc.scanRepoStructure(tempFile.absolutePath)
            assertTrue(
                result.contains("not a directory") || result.contains("access denied") || result.contains("not found"),
                "Expected directory-related error, got: $result",
            )
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `shellRead returns output for valid command`() {
        val svc = makeService()
        val result = svc.shellRead("echo", "hello-from-shell")
        assertEquals("hello-from-shell\n", result)
    }

    @Test
    fun `shellRead returns null for nonexistent command`() {
        val svc = makeService()
        val result = svc.shellRead("no-such-command-xyz-abc")
        assertNull(result)
    }

    @Test
    fun `shellRead returns null for blank output`() {
        val svc = makeService()

        val result = svc.shellRead("sh", "-c", "true")
        assertNull(result)
    }
}
