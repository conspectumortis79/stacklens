package com.stacklens.application.service

import com.stacklens.adapter.out.textfilter.DefaultAiResponseJsonExtractor
import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SqlAiServicePromptAndFilterCoverageTest {
    private val copilotCliService: CopilotPort = mock()
    private val promptLoader: PromptReaderPort = mock()
    private val jsonCodec: JsonCodecPort = mock()
    private val service =
        SqlAiService(
            copilotCliService = copilotCliService,
            promptLoader = promptLoader,
            jsonCodec = jsonCodec,
            aiJsonExtractor = DefaultAiResponseJsonExtractor(),
        )

    private val requestedKeys = mutableListOf<String>()

    private fun stubPrompts() {
        whenever(copilotCliService.resolveLanguage()).thenReturn("en")
        whenever(promptLoader.get(any(), any(), any()))
            .thenAnswer { invocation ->
                requestedKeys += invocation.arguments[1] as String
                invocation.arguments[1] as String
            }
    }

    @Test
    fun `a blank ddl is treated as no ddl in the analyze prompt`() {
        stubPrompts()
        assertEquals("sql.analyzeQuery", service.buildAnalyzePrompt("SELECT 1", "   "))
        assertEquals(listOf("sql.analyzeQuery"), requestedKeys)
    }

    @Test
    fun `a blank ddl is treated as no ddl in the sample data prompt`() {
        stubPrompts()
        assertEquals("sql.sampleData", service.buildSampleDataPrompt("SELECT 1", "   "))
    }

    @Test
    fun `a blank seed data is treated as no seed data in the generate prompt`() {
        stubPrompts()
        assertEquals("sql.generateQuery", service.buildGenerateQueryPrompt("CREATE TABLE t (id INT)", "  ", "goal"))
    }

    @Test
    fun `a present ddl renders the ddl section`() {
        stubPrompts()
        assertEquals("sql.analyzeQuery", service.buildAnalyzePrompt("SELECT 1", "CREATE TABLE t (id INT)"))
        assertEquals("sql.sampleData", service.buildSampleDataPrompt("SELECT 1", "CREATE TABLE t (id INT)"))
        assertEquals("sql.generateQuery", service.buildGenerateQueryPrompt("ddl", "INSERT INTO t VALUES (1)", "goal"))
        assertEquals(listOf("sql.ddlSection", "sql.analyzeQuery", "sql.ddlSection", "sql.sampleData", "sql.seedSection", "sql.generateQuery"), requestedKeys)
    }

    @Test
    fun `blank entries are filtered out of the query analysis lists`() {
        whenever(jsonCodec.decodeMap(any<String>())).thenReturn(
            mapOf(
                "syntaxIssues" to listOf("ok", "   ", 12),
                "indexSuggestions" to listOf(" ", "index on t(id)"),
                "optimizations" to listOf(mapOf("title" to "t", "description" to "d", "suggestion" to "s")),
            ),
        )

        val result = service.parseQueryAnalysis("{}")

        assertEquals(listOf("ok", "12"), result.syntaxIssues)
        assertEquals(listOf("index on t(id)"), result.indexSuggestions)
        assertEquals("MITTEL", result.optimizations.single().severity)
    }

    @Test
    fun `blank entries are filtered out of the sample data lists`() {
        whenever(jsonCodec.decodeMap(any<String>())).thenReturn(
            mapOf(
                "tables" to listOf(mapOf("tableName" to "t", "insertStatements" to listOf(" ", "INSERT 1", "x"))),
                "executionOrder" to listOf("t", ""),
            ),
        )

        val result = service.parseSampleData("{}")

        assertEquals(listOf("INSERT 1", "x"), result.tables.single().insertStatements)
        assertEquals(listOf("t"), result.executionOrder)
        assertNull(result.tables.single().notes)
    }

    @Test
    fun `blank entries are filtered out of the generated query tables`() {
        whenever(jsonCodec.decodeMap(any<String>())).thenReturn(
            mapOf(
                "query" to "SELECT 1",
                "usedTables" to listOf("t", "  ", mapOf("statement" to "FROM other")),
                "notes" to " ",
            ),
        )

        val result = service.parseGeneratedQuery("{}")

        assertEquals(listOf("t", "FROM other"), result.usedTables)
        assertNull(result.notes)
    }

    @Test
    fun `node text falls back to the string form when no known key holds a value`() {
        assertEquals("plain", service.nodeText("plain"))
        assertEquals("", service.nodeText(null))
        assertEquals("query text", service.nodeText(mapOf("query" to "query text")))
        assertEquals("{other=value}", service.nodeText(mapOf("other" to "value")))
    }
}
