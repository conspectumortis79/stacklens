package com.stacklens.adapter.out.ai

import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.domain.model.knowledge.KnowledgeSourceType
import com.stacklens.domain.model.knowledge.RetrievedKnowledge
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertIterableEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CopilotKnowledgeRerankAdapterTest {
    private val copilot: CopilotPort = mock()
    private val adapter = CopilotKnowledgeRerankAdapter(copilot)

    @Test
    fun `rewriteQueryForSearch returns null when provider not ready`() {
        whenever(copilot.isReady()).thenReturn(false)
        assertNull(adapter.rewriteQueryForSearch("Wie crasht MCP?", 256))
    }

    @Test
    fun `rewriteQueryForSearch returns null on blank question`() {
        whenever(copilot.isReady()).thenReturn(true)
        assertNull(adapter.rewriteQueryForSearch("", 256))
        assertNull(adapter.rewriteQueryForSearch("   ", 256))
    }

    @Test
    fun `rewriteQueryForSearch returns trimmed response on success`() {
        whenever(copilot.isReady()).thenReturn(true)
        whenever(copilot.resolveModel()).thenReturn("gpt-5.4-mini")
        whenever(copilot.execute(eq("gpt-5.4-mini"), any(), anyOrNull(), anyOrNull()))
            .thenReturn("  MCP-Server Absturz Ursache Fehler exception  \n")
        val out = adapter.rewriteQueryForSearch("Warum crasht der MCP-Server?", 256)
        assertEquals("MCP-Server Absturz Ursache Fehler exception", out)
    }

    @Test
    fun `rewriteQueryForSearch truncates the response to the maxOutputTokens budget`() {
        whenever(copilot.isReady()).thenReturn(true)
        whenever(copilot.resolveModel()).thenReturn("gpt-5.4-mini")
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn("x".repeat(1000))

        val out = adapter.rewriteQueryForSearch("Frage", maxOutputTokens = 10)

        assertEquals(10 * CopilotKnowledgeRerankAdapter.CHARS_PER_TOKEN, out!!.length)
    }

    @Test
    fun `rerankChunks uses the provider-resolved model`() {
        whenever(copilot.isReady()).thenReturn(true)
        whenever(copilot.resolveModel()).thenReturn("ollama-model")
        whenever(copilot.execute(eq("ollama-model"), any(), anyOrNull(), anyOrNull())).thenReturn("1 0 2 3")
        val candidates = (0L until 6L).map { sample(it) }

        val out = adapter.rerankChunks("Frage", candidates, finalCount = 3, maxOutputTokens = 512)

        assertEquals(listOf(1L, 0L, 2L), out!!.map { it.chunkId })
        verify(copilot).execute(eq("ollama-model"), any(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `rewriteQueryForSearch returns null on provider exception`() {
        whenever(copilot.isReady()).thenReturn(true)
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenThrow(RuntimeException("network down"))
        assertNull(adapter.rewriteQueryForSearch("Frage", 256))
    }

    @Test
    fun `rewriteQueryForSearch returns null on empty LLM response`() {
        whenever(copilot.isReady()).thenReturn(true)
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn("   \n  ")
        assertNull(adapter.rewriteQueryForSearch("Frage", 256))
    }

    @Test
    fun `rerankChunks returns null when provider not ready`() {
        whenever(copilot.isReady()).thenReturn(false)
        val candidates = listOf(sample(0), sample(1))
        assertNull(adapter.rerankChunks("Frage", candidates, 2, 512))
    }

    @Test
    fun `rerankChunks returns input unchanged when candidate list is already short`() {
        whenever(copilot.isReady()).thenReturn(true)
        val candidates = listOf(sample(0L), sample(1L), sample(2L))
        val out = adapter.rerankChunks("Frage", candidates, finalCount = 5, maxOutputTokens = 512)
        assertIterableEquals(candidates, out)
    }

    @Test
    fun `rerankChunks returns null on provider exception`() {
        whenever(copilot.isReady()).thenReturn(true)
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenThrow(RuntimeException("network down"))
        val candidates = (0L until 10L).map { sample(it) }
        assertNull(adapter.rerankChunks("Frage", candidates, 5, 512))
    }

    @Test
    fun `parseRerankResponse returns Lucene order on empty response`() {
        val candidates = (0L until 5L).map { sample(it) }
        val out = adapter.parseRerankResponse("", candidates, finalCount = 3)
        assertIterableEquals(candidates.take(3), out)
    }

    @Test
    fun `parseRerankResponse returns Lucene order on non-numeric response`() {
        val candidates = (0L until 5L).map { sample(it) }
        val out = adapter.parseRerankResponse("I don't know, sorry", candidates, finalCount = 3)
        assertIterableEquals(candidates.take(3), out)
    }

    @Test
    fun `parseRerankResponse honours LLM ordering`() {
        val candidates = (0L until 5L).map { sample(it) }
        val out = adapter.parseRerankResponse("3 1 4", candidates, finalCount = 3)
        assertIterableEquals(listOf(3L, 1L, 4L), out.map { it.chunkId })
    }

    @Test
    fun `parseRerankResponse dedupes duplicate indices`() {
        val candidates = (0L until 5L).map { sample(it) }
        val out = adapter.parseRerankResponse("2 2 2 0", candidates, finalCount = 3)
        assertIterableEquals(listOf(2L, 0L, 1L), out.map { it.chunkId })
    }

    @Test
    fun `parseRerankResponse filters out-of-range indices and pads`() {
        val candidates = (0L until 5L).map { sample(it) }
        val out = adapter.parseRerankResponse("99 2 -1 0 100", candidates, finalCount = 3)
        assertIterableEquals(listOf(2L, 0L, 1L), out.map { it.chunkId })
    }

    @Test
    fun `parseRerankResponse tolerates comma-separated indices`() {
        val candidates = (0L until 5L).map { sample(it) }
        val out = adapter.parseRerankResponse("4, 2, 0", candidates, finalCount = 3)
        assertIterableEquals(listOf(4L, 2L, 0L), out.map { it.chunkId })
    }

    @Test
    fun `parseRerankResponse caps result at finalCount even when LLM returns more`() {
        val candidates = (0L until 5L).map { sample(it) }
        val out = adapter.parseRerankResponse("0 1 2 3 4", candidates, finalCount = 2)
        assertEquals(2, out.size)
        assertIterableEquals(listOf(0L, 1L), out.map { it.chunkId })
    }

    @Test
    fun `rewrite prompt contains the original question`() {
        val captured = org.mockito.kotlin.argumentCaptor<String>()
        whenever(copilot.isReady()).thenReturn(true)
        whenever(copilot.resolveModel()).thenReturn("gpt-5.4-mini")
        whenever(copilot.execute(any(), captured.capture(), anyOrNull(), anyOrNull()))
            .thenReturn("MCP crash")
        val out = adapter.rewriteQueryForSearch("Warum crasht der MCP-Server?", 256)
        assertNotNull(out)
        assertTrue(
            captured.firstValue.contains("Warum crasht der MCP-Server?"),
            "rewrite prompt must contain the original question for the LLM to base the rewrite on",
        )
    }

    @Test
    fun `rerank prompt contains every candidate index for the LLM to pick`() {
        val captured = org.mockito.kotlin.argumentCaptor<String>()
        val candidates = (0L until 4L).map { sample(it) }
        whenever(copilot.isReady()).thenReturn(true)
        whenever(copilot.resolveModel()).thenReturn("gpt-5.4-mini")
        whenever(copilot.execute(any(), captured.capture(), anyOrNull(), anyOrNull())).thenReturn("0 1 2")
        adapter.rerankChunks("Frage", candidates, finalCount = 3, maxOutputTokens = 512)
        val prompt = captured.firstValue
        for (i in candidates.indices) {
            assertTrue(prompt.contains("[$i]"), "rerank prompt must list every candidate with its index, missing [$i]")
        }
        assertTrue(prompt.contains("Frage"), "rerank prompt must include the user's question")
        assertTrue(prompt.contains("3 "), "rerank prompt must ask for exactly 3 indices")
    }

    private fun sample(chunkId: Long) =
        RetrievedKnowledge(
            chunkId = chunkId,
            documentId = chunkId,
            knowledgeBaseId = 1L,
            title = "Chunk $chunkId",
            sourceType = KnowledgeSourceType.MANUAL,
            sourceUri = null,
            content = "content $chunkId",
            score = 1.0 - chunkId * 0.01,
            citation = "KB:document-$chunkId#chunk-0",
        )

    @Test
    fun `rewriteQueryForSearch returns null when the token budget truncates the answer to blank`() {
        whenever(copilot.isReady()).thenReturn(true)
        whenever(copilot.resolveModel()).thenReturn("gpt-5.4-mini")
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn("abc")

        assertNull(adapter.rewriteQueryForSearch("Frage", maxOutputTokens = 0))
    }
}
