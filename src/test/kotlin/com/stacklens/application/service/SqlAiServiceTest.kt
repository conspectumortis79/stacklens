package com.stacklens.application.service

import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SqlAiServiceTest {
    private val copilot = mock<CopilotPort>()
    private val promptLoader = mock<PromptReaderPort>()
    private val jsonCodec = mock<JsonCodecPort>()
    private val service =
        SqlAiService(
            copilot,
            promptLoader,
            jsonCodec,
            com.stacklens.adapter.out.textfilter
                .DefaultAiResponseJsonExtractor(),
        )

    @BeforeEach
    fun setUp() {
        whenever(copilot.resolveLanguage()).thenReturn("en")
        whenever(promptLoader.get(any<String>(), any<String>(), any<Map<String, String>>())).thenReturn("PROMPT")
    }

    @Test
    fun `buildAnalyzePrompt with DDL includes DDL section`() {
        val prompt = service.buildAnalyzePrompt("SELECT 1", "CREATE TABLE x (id INT)")
        assertEquals("PROMPT", prompt)
    }

    @Test
    fun `buildAnalyzePrompt without DDL omits DDL section`() {
        val prompt = service.buildAnalyzePrompt("SELECT 1", null)
        assertEquals("PROMPT", prompt)
    }

    @Test
    fun `buildAnalyzePrompt with blank DDL omits DDL section`() {
        val prompt = service.buildAnalyzePrompt("SELECT 1", "   ")
        assertEquals("PROMPT", prompt)
    }

    @Test
    fun `buildSampleDataPrompt with DDL includes DDL section`() {
        val prompt = service.buildSampleDataPrompt("SELECT 1", "CREATE TABLE x (id INT)")
        assertEquals("PROMPT", prompt)
    }

    @Test
    fun `buildSampleDataPrompt without DDL omits DDL section`() {
        val prompt = service.buildSampleDataPrompt("SELECT 1", null)
        assertEquals("PROMPT", prompt)
    }

    @Test
    fun `buildGenerateQueryPrompt with seed data includes it`() {
        val prompt = service.buildGenerateQueryPrompt("CREATE TABLE x", "seed-data", "find users")
        assertTrue(prompt.contains("PROMPT"))
    }

    @Test
    fun `buildGenerateQueryPrompt without seed data works`() {
        val prompt = service.buildGenerateQueryPrompt("CREATE TABLE x", null, "find users")
        assertEquals("PROMPT", prompt)
    }
}
