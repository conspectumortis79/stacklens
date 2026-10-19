package com.stacklens.adapter.out.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DefaultAiUsageExtractorTest {
    private val extractor = DefaultAiUsageExtractor()

    @Test
    fun `extracts OpenAI usage block`() {
        val body = """{"id":"x","choices":[],"usage":{"prompt_tokens":42,"completion_tokens":7,"total_tokens":49}}"""
        val r = extractor.extract(body, "OpenAI")
        assertNotNull(r)
        assertEquals(42L, r!!.promptTokens)
        assertEquals(7L, r.completionTokens)
    }

    @Test
    fun `extracts OpenRouter usage block`() {
        val body = """{"usage":{"prompt_tokens":100,"completion_tokens":50,"total_tokens":150}}"""
        val r = extractor.extract(body, "OpenRouter")
        assertNotNull(r)
        assertEquals(100L, r!!.promptTokens)
        assertEquals(50L, r.completionTokens)
    }

    @Test
    fun `extracts LM Studio usage block (case-insensitive label)`() {
        val body = """{"usage":{"prompt_tokens":3,"completion_tokens":9,"total_tokens":12}}"""
        val r = extractor.extract(body, "LM Studio")
        assertNotNull(r)
        assertEquals(3L, r!!.promptTokens)
        assertEquals(9L, r.completionTokens)
    }

    @Test
    fun `extracts LmStudio usage block (camelCase label)`() {
        val body = """{"usage":{"prompt_tokens":3,"completion_tokens":9,"total_tokens":12}}"""
        val r = extractor.extract(body, "LmStudio")
        assertNotNull(r)
        assertEquals(3L, r!!.promptTokens)
        assertEquals(9L, r.completionTokens)
    }

    @Test
    fun `extracts Ollama OpenAI-compat usage block when present`() {
        val body = """{"choices":[],"usage":{"prompt_tokens":20,"completion_tokens":5,"total_tokens":25}}"""
        val r = extractor.extract(body, "Ollama")
        assertNotNull(r)
        assertEquals(20L, r!!.promptTokens)
        assertEquals(5L, r.completionTokens)
    }

    @Test
    fun `falls back to Ollama native fields when no usage block`() {
        val body = """{"model":"qwen3","prompt_eval_count":77,"eval_count":13,"done":true}"""
        val r = extractor.extract(body, "Ollama")
        assertNotNull(r)
        assertEquals(77L, r!!.promptTokens)
        assertEquals(13L, r.completionTokens)
    }

    @Test
    fun `Copilot returns null so caller falls back to estimation`() {
        val body = """{"event":"content","text":"hello"}"""
        val r = extractor.extract(body, "Copilot")
        assertNull(r)
    }

    @Test
    fun `extracts Claude usage block (Anthropic input_tokens and output_tokens)`() {
        val body = """{"id":"msg_1","content":[{"type":"text","text":"ok"}],"usage":{"input_tokens":15,"output_tokens":4}}"""
        val r = extractor.extract(body, "ClaudeCode")
        assertNotNull(r)
        assertEquals(15L, r!!.promptTokens)
        assertEquals(4L, r.completionTokens)
    }

    @Test
    fun `extracts Claude usage block with mixed-case label`() {
        val body = """{"usage":{"input_tokens":1,"output_tokens":2}}"""
        assertNotNull(extractor.extract(body, "claude"))
        assertNotNull(extractor.extract(body, "CLAUDE"))
    }

    @Test
    fun `Claude usage block returns null when input_tokens is missing`() {
        val body = """{"usage":{"output_tokens":4}}"""
        assertNull(extractor.extract(body, "ClaudeCode"))
    }

    @Test
    fun `Claude usage block returns null when output_tokens is missing`() {
        val body = """{"usage":{"input_tokens":15}}"""
        assertNull(extractor.extract(body, "ClaudeCode"))
    }

    @Test
    fun `Claude usage block returns null for negative token counts`() {
        val body = """{"usage":{"input_tokens":-1,"output_tokens":-4}}"""
        assertNull(extractor.extract(body, "ClaudeCode"))
    }

    @Test
    fun `Claude usage block returns null when usage object is missing`() {
        val body = """{"content":[{"type":"text","text":"hi"}]}"""
        assertNull(extractor.extract(body, "ClaudeCode"))
    }

    @Test
    fun `Claude does not pick up OpenAI-style usage block`() {
        val body = """{"usage":{"prompt_tokens":42,"completion_tokens":7,"total_tokens":49}}"""
        assertNull(extractor.extract(body, "ClaudeCode"))
    }

    @Test
    fun `blank body returns null`() {
        assertNull(extractor.extract("", "OpenAI"))
        assertNull(extractor.extract("   ", "OpenAI"))
    }

    @Test
    fun `non-JSON body returns null`() {
        assertNull(extractor.extract("not json", "OpenAI"))
    }

    @Test
    fun `JSON without usage returns null for OpenAI`() {
        val body = """{"choices":[]}"""
        assertNull(extractor.extract(body, "OpenAI"))
    }

    @Test
    fun `partial usage block (missing completion_tokens) returns null`() {
        val body = """{"usage":{"prompt_tokens":1}}"""
        assertNull(extractor.extract(body, "OpenAI"))
    }

    @Test
    fun `negative token counts cause extraction to fail (defensive)`() {
        val body = """{"usage":{"prompt_tokens":-1,"completion_tokens":-5,"total_tokens":-6}}"""
        assertNull(extractor.extract(body, "OpenAI"))
    }

    @Test
    fun `unknown provider label returns null`() {
        val body = """{"usage":{"prompt_tokens":1,"completion_tokens":2,"total_tokens":3}}"""
        assertNull(extractor.extract(body, "UnknownProvider"))
    }

    @Test
    fun `Ollama native returns null when prompt_eval_count is missing`() {
        val body = """{"model":"qwen3","eval_count":13}"""
        assertNull(extractor.extract(body, "Ollama"))
    }

    @Test
    fun `Ollama native returns null when eval_count is missing`() {
        val body = """{"model":"qwen3","prompt_eval_count":77}"""
        assertNull(extractor.extract(body, "Ollama"))
    }

    @Test
    fun `Ollama native returns null when both counts are missing`() {
        val body = """{"model":"qwen3","done":true}"""
        assertNull(extractor.extract(body, "Ollama"))
    }

    @Test
    fun `Ollama native returns null when prompt_eval_count is negative`() {
        val body = """{"model":"qwen3","prompt_eval_count":-1,"eval_count":13}"""
        assertNull(extractor.extract(body, "Ollama"))
    }

    @Test
    fun `Ollama native returns null when eval_count is negative`() {
        val body = """{"model":"qwen3","prompt_eval_count":77,"eval_count":-1}"""
        assertNull(extractor.extract(body, "Ollama"))
    }

    @Test
    fun `OpenAI usage block returns null when prompt_tokens is missing`() {
        val body = """{"usage":{"completion_tokens":42}}"""
        assertNull(extractor.extract(body, "OpenAI"))
    }

    @Test
    fun `OpenAI usage block returns null when completion_tokens is missing`() {
        val body = """{"usage":{"prompt_tokens":42}}"""
        assertNull(extractor.extract(body, "OpenAI"))
    }

    @Test
    fun `extract returns null when body parses to null token`() {
        assertNull(extractor.extract("null", "OpenAI"))
    }

    @Test
    fun `extract returns null when body is invalid json`() {
        assertNull(extractor.extract("{not valid json", "OpenAI"))
    }
}
