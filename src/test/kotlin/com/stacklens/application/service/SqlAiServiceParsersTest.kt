package com.stacklens.application.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SqlAiServiceParsersTest {
    private val copilotCliService: CopilotPort = mock()
    private val promptLoader: PromptReaderPort = mock()
    private val objectMapper = ObjectMapper()
    private val jsonCodec: JsonCodecPort = mock()

    private fun service() =
        SqlAiService(
            copilotCliService = copilotCliService,
            promptLoader = promptLoader,
            jsonCodec = jsonCodec,
            aiJsonExtractor =
                com.stacklens.adapter.out.textfilter
                    .DefaultAiResponseJsonExtractor(),
        )

    @Suppress("UNCHECKED_CAST")
    private fun mockDecodeMap(json: String): Map<String, Any?> {
        val map = objectMapper.readValue(json, Map::class.java) as Map<String, Any?>
        whenever(jsonCodec.decodeMap(json)).thenReturn(map)
        return map
    }

    @Test
    fun `parseQueryAnalysis parses a complete response`() {
        val json =
            """{
            "syntaxStatus": "VALID",
            "syntaxDetails": "All good",
            "syntaxIssues": ["Issue 1", "Issue 2"],
            "optimizations": [
              {"title": "Add index", "description": "Faster lookups", "severity": "HIGH", "suggestion": "CREATE INDEX..."}
            ],
            "indexSuggestions": ["idx_users_email"],
            "insights": "Query is well-formed"
        }"""
        mockDecodeMap(json)
        val result = service().parseQueryAnalysis(json)
        assertEquals("VALID", result.syntaxStatus)
        assertEquals("All good", result.syntaxDetails)
        assertEquals(2, result.syntaxIssues.size)
        assertEquals(1, result.optimizations.size)
        assertEquals("Add index", result.optimizations[0].title)
        assertEquals("HIGH", result.optimizations[0].severity)
        assertEquals(1, result.indexSuggestions.size)
        assertEquals("Query is well-formed", result.insights)
    }

    @Test
    fun `parseQueryAnalysis defaults severity to MITTEL when blank`() {
        val json =
            """{"optimizations":[{"title":"X","description":"Y","severity":"","suggestion":"Z"}]}"""
        mockDecodeMap(json)
        val result = service().parseQueryAnalysis(json)
        assertEquals("MITTEL", result.optimizations[0].severity)
    }

    @Test
    fun `parseQueryAnalysis returns fallback on parse failure`() {
        whenever(jsonCodec.decodeMap(any<String>())).thenThrow(RuntimeException("parse failed"))
        val result = service().parseQueryAnalysis("not json at all")
        assertEquals("UNBEKANNT", result.syntaxStatus)
        assertEquals("Analysis could not be parsed.", result.syntaxDetails)
        assertTrue(result.optimizations.isEmpty())
        assertTrue(result.syntaxIssues.isEmpty())
    }

    @Test
    fun `parseQueryAnalysis defaults syntaxStatus to UNBEKANNT when blank`() {
        val json = """{"syntaxStatus":""}"""
        mockDecodeMap(json)
        val result = service().parseQueryAnalysis(json)
        assertEquals("UNBEKANNT", result.syntaxStatus)
    }

    @Test
    fun `parseQueryAnalysis extracts JSON block from response with surrounding text`() {
        val response =
            """Here is the analysis:
```json
{"syntaxStatus":"OK","syntaxDetails":"fine"}
```
Done."""
        mockDecodeMap("""{"syntaxStatus":"OK","syntaxDetails":"fine"}""")
        val result = service().parseQueryAnalysis(response)
        assertEquals("OK", result.syntaxStatus)
        assertEquals("fine", result.syntaxDetails)
    }

    @Test
    fun `parseSampleData parses a complete response`() {
        val json =
            """{
            "tables": [
              {
                "tableName": "users",
                "createStatement": "CREATE TABLE users (id INT)",
                "insertStatements": ["INSERT INTO users VALUES (1)"],
                "notes": "Primary table"
              }
            ],
            "executionOrder": ["users"],
            "notes": "Seed data",
            "validationNotes": "All valid"
        }"""
        mockDecodeMap(json)
        val result = service().parseSampleData(json)
        assertEquals(1, result.tables.size)
        assertEquals("users", result.tables[0].tableName)
        assertEquals("CREATE TABLE users (id INT)", result.tables[0].createStatement)
        assertEquals(1, result.tables[0].insertStatements.size)
        assertEquals("Primary table", result.tables[0].notes)
        assertEquals(1, result.executionOrder.size)
        assertEquals("Seed data", result.notes)
        assertEquals("All valid", result.validationNotes)
    }

    @Test
    fun `parseSampleData handles empty arrays`() {
        val json = """{"tables":[]}"""
        mockDecodeMap(json)
        val result = service().parseSampleData(json)
        assertTrue(result.tables.isEmpty())
    }

    @Test
    fun `parseSampleData returns fallback on parse failure`() {
        whenever(jsonCodec.decodeMap(any<String>())).thenThrow(RuntimeException("parse failed"))
        val result = service().parseSampleData("garbage")
        assertTrue(result.tables.isEmpty())
        assertTrue(result.executionOrder.isEmpty())
    }

    @Test
    fun `parseGeneratedQuery parses a complete response`() {
        val json =
            """{
            "query": "SELECT * FROM users",
            "explanation": "Returns all users",
            "usedTables": ["users"],
            "alternativeQueries": [
              {"query": "SELECT id FROM users", "description": "Just IDs"}
            ],
            "notes": "Use sparingly"
        }"""
        mockDecodeMap(json)
        val result = service().parseGeneratedQuery(json)
        assertEquals("SELECT * FROM users", result.query)
        assertEquals("Returns all users", result.explanation)
        assertEquals(1, result.usedTables.size)
        assertEquals(1, result.alternativeQueries.size)
        assertEquals("SELECT id FROM users", result.alternativeQueries[0].query)
        assertEquals("Just IDs", result.alternativeQueries[0].description)
        assertEquals("Use sparingly", result.notes)
    }

    @Test
    fun `parseGeneratedQuery returns fallback on parse failure`() {
        whenever(jsonCodec.decodeMap(any<String>())).thenThrow(RuntimeException("parse failed"))
        val result = service().parseGeneratedQuery("garbage")
        assertEquals("", result.query)
        assertEquals("Response could not be parsed.", result.explanation)
        assertTrue(result.usedTables.isEmpty())
        assertTrue(result.alternativeQueries.isEmpty())
    }

    @Test
    fun `parseGeneratedQuery handles empty alternative queries`() {
        val json = """{"query":"SELECT 1","explanation":"trivial","usedTables":[],"alternativeQueries":[]}"""
        mockDecodeMap(json)
        val result = service().parseGeneratedQuery(json)
        assertEquals("SELECT 1", result.query)
        assertEquals("trivial", result.explanation)
        assertTrue(result.alternativeQueries.isEmpty())
    }

    @Test
    fun `parseQueryAnalysis handles missing syntaxIssues field`() {
        val json = """{"summary":"x","query":"SELECT 1","severity":"HOCH","syntaxStatus":"OK","indexSuggestions":["idx1"]}"""
        mockDecodeMap(json)
        val result = service().parseQueryAnalysis(json)
        assertTrue(result.syntaxIssues.isEmpty())
        assertEquals(1, result.indexSuggestions.size)
    }

    @Test
    fun `parseQueryAnalysis handles missing indexSuggestions field`() {
        val json = """{"summary":"x","query":"SELECT 1","severity":"HOCH","syntaxStatus":"OK","syntaxIssues":["issue1"]}"""
        mockDecodeMap(json)
        val result = service().parseQueryAnalysis(json)
        assertEquals(1, result.syntaxIssues.size)
        assertTrue(result.indexSuggestions.isEmpty())
    }

    @Test
    fun `parseSampleData handles missing insertStatements field`() {
        val json = """{"tables":[{"tableName":"users","createStatement":"CREATE TABLE users"}]}"""
        mockDecodeMap(json)
        val result = service().parseSampleData(json)
        assertEquals(1, result.tables.size)
        assertEquals("users", result.tables[0].tableName)
        assertTrue(result.tables[0].insertStatements.isEmpty())
    }
}
