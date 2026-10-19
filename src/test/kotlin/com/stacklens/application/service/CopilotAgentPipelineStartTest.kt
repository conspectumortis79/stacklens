package com.stacklens.application.service

import com.stacklens.application.port.model.pipeline.MockupImage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import java.io.File

class CopilotAgentPipelineStartTest : CopilotAgentPipelineServiceTestBase() {
    @Test
    fun `start creates a run and returns id`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer {
            @Suppress("UNCHECKED_CAST")
            val onChunk = it.arguments[3] as (String) -> Unit
            onChunk("partial")
            "output text"
        }
        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("Add JWT auth", null)
        assertTrue(id > 0)
    }

    @Test
    fun `start returns incrementing ids`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")
        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id1 = svc.start("first", null)
        val id2 = svc.start("second", null)
        assertTrue(id2 > id1)
    }

    @Test
    fun `start with non-blank mockupBase64 includes it in run mockupImages when mockupImages empty`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")
        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("feature", null, emptyList(), "base64data", "image/png", emptyList())
        val run = svc.get(id)
        assertNotNull(run)
        assertEquals(1, run!!.mockupImages.size)
        assertEquals("base64data", run.mockupImages[0].base64)
        assertEquals("image/png", run.mockupImages[0].mimeType)
    }

    @Test
    fun `start with blank mockupBase64 results in empty mockupImages when mockupImages empty`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")
        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("feature", null, emptyList(), "   ", "image/png", emptyList())
        val run = svc.get(id)
        assertNotNull(run)
        assertTrue(run!!.mockupImages.isEmpty())
    }

    @Test
    fun `start with null mockupBase64 results in empty mockupImages when mockupImages empty`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")
        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("feature", null, emptyList(), null, null, emptyList())
        val run = svc.get(id)
        assertNotNull(run)
        assertTrue(run!!.mockupImages.isEmpty())
    }

    @Test
    fun `start with non-null localRepoConfigId where config not found results in null repoPath`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")
        whenever(localRepoConfigPort.findLocalRepoConfigById(99L)).thenReturn(null)
        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("feature", localRepoConfigId = 99L)
        val run = svc.get(id)
        assertNotNull(run)
        assertNull(run!!.repoPath)
    }

    @Test
    fun `start with non-empty mockupImages uses them directly without mockupBase64`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")
        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val images =
            listOf(
                MockupImage("existingB64", "image/webp"),
            )
        val id = svc.start("feature", null, emptyList(), "anotherB64", "image/png", images)
        val run = svc.get(id)
        assertNotNull(run)

        assertEquals(1, run!!.mockupImages.size)
        assertEquals("existingB64", run.mockupImages[0].base64)
    }

    @Test
    fun `start with non-blank mockupBase64 and null mockupMimeType uses empty string as mimeType`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("ok")
        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("feature", null, emptyList(), "base64data", null, emptyList())
        val run = svc.get(id)
        assertNotNull(run)
        assertEquals(1, run!!.mockupImages.size)
        assertEquals("", run.mockupImages[0].mimeType)
    }
}
