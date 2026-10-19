package com.stacklens.adapter.out.jira

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.test.util.ReflectionTestUtils

class AdfDescriptionUpdaterHelpersTest {
    private val mapper = ObjectMapper()

    private fun updater(): AdfDescriptionUpdater =
        AdfDescriptionUpdater(
            jiraTicketHttp = org.mockito.kotlin.mock<JiraTicketHttpClient>(),
            objectMapper = mapper,
            mdConverter = org.mockito.kotlin.mock<JiraMarkdownConverter>(),
        )

    private fun invoke(
        name: String,
        vararg args: Any?,
    ): Any? = ReflectionTestUtils.invokeMethod(updater(), name, *args)

    @Test
    fun `appendAdfToDescription preserves existing ADF paragraphs on Cloud`() {
        val existingAdfJson =
            """
            {
              "type": "doc",
              "version": 1,
              "content": [
                {"type":"paragraph","content":[{"type":"text","text":"User Story: ...","marks":[{"type":"strong"}]}]},
                {"type":"paragraph","content":[{"type":"text","text":"Akzeptanzkriterien: ..."}]}
              ]
            }
            """.trimIndent()
        val newAdfTable =
            mapOf(
                "type" to "table",
                "content" to
                    listOf(
                        mapOf(
                            "type" to "tableRow",
                            "content" to
                                listOf(
                                    mapOf("type" to "tableCell", "content" to listOf(mapOf("type" to "paragraph", "content" to listOf(mapOf("type" to "text", "text" to "PROJ-1"))))),
                                ),
                        ),
                    ),
            )
        val newAdfDoc =
            mapOf(
                "type" to "doc",
                "version" to 1,
                "content" to listOf(newAdfTable),
            )

        val existingNode: JsonNode = mapper.readTree(existingAdfJson)

        @Suppress("UNCHECKED_CAST")
        val existingContent =
            invoke("adfContentAsMapList", existingNode.path("content")) as List<Map<String, Any?>>
        assertEquals(2, existingContent.size, "ArrayNode→List conversion must yield both existing paragraphs")
        assertEquals("paragraph", existingContent[0]["type"])
        assertEquals("paragraph", existingContent[1]["type"])

        @Suppress("UNCHECKED_CAST")
        val newContent =
            invoke("adfContentAsMapList", newAdfDoc["content"]) as List<Map<String, Any?>>
        assertEquals(1, newContent.size)
        assertEquals("table", newContent[0]["type"])

        val merged = existingContent + newContent
        assertEquals(3, merged.size)
        assertEquals("paragraph", merged[0]["type"])
        assertEquals("paragraph", merged[1]["type"])
        assertEquals("table", merged[2]["type"])
    }

    @Test
    fun `appendAdfToDescription is robust when existing content is missing`() {
        val nodeWithoutContent: JsonNode = mapper.readTree("""{"type":"doc","version":1}""")

        @Suppress("UNCHECKED_CAST")
        val result =
            invoke("adfContentAsMapList", nodeWithoutContent.path("content")) as List<Map<String, Any?>>
        assertTrue(result.isEmpty())
    }

    @Test
    fun `prependGreeting adds a greeting paragraph before the existing content`() {
        val adf = """{"type":"doc","version":1,"content":[{"type":"paragraph","content":[{"type":"text","text":"orig"}]}]}"""

        val result = updater().prependGreeting(adf, "Hallo", "Offene Punkte", emptyList())

        @Suppress("UNCHECKED_CAST")
        val doc = mapper.readValue(result, Map::class.java) as Map<String, Any?>

        @Suppress("UNCHECKED_CAST")
        val content = doc["content"] as List<Map<String, Any?>>
        assertEquals(2, content.size)
        @Suppress("UNCHECKED_CAST")
        val greetingInlines = content[0]["content"] as List<Map<String, Any?>>
        assertEquals("Hallo", greetingInlines[0]["text"])
        @Suppress("UNCHECKED_CAST")
        val originalInlines = content[1]["content"] as List<Map<String, Any?>>
        assertEquals("orig", originalInlines[0]["text"])
    }

    @Test
    fun `prependGreeting appends a rule heading and bullet list for open points`() {
        val adf = """{"type":"doc","version":1,"content":[]}"""

        val result = updater().prependGreeting(adf, "Hallo", "Offene Punkte", listOf("P1", "P2"))

        @Suppress("UNCHECKED_CAST")
        val doc = mapper.readValue(result, Map::class.java) as Map<String, Any?>

        @Suppress("UNCHECKED_CAST")
        val content = doc["content"] as List<Map<String, Any?>>
        assertEquals(4, content.size)
        assertEquals("rule", content[1]["type"])
        assertEquals("heading", content[2]["type"])
        @Suppress("UNCHECKED_CAST")
        val headingInlines = content[2]["content"] as List<Map<String, Any?>>
        assertEquals("⚠ Offene Punkte", headingInlines[0]["text"])
        assertEquals("bulletList", content[3]["type"])
        @Suppress("UNCHECKED_CAST")
        val items = content[3]["content"] as List<Map<String, Any?>>
        assertEquals(2, items.size)
        assertTrue(result.contains("P1"))
        assertTrue(result.contains("P2"))
    }

    @Test
    fun `prependGreeting returns blank input unchanged`() {
        assertEquals("", updater().prependGreeting("", "Hallo", "Offene Punkte", emptyList()))
        assertEquals("   ", updater().prependGreeting("   ", "Hallo", "Offene Punkte", emptyList()))
    }

    @Test
    fun `prependGreeting returns input unchanged for invalid ADF`() {
        assertEquals("not json", updater().prependGreeting("not json", "Hallo", "Offene Punkte", emptyList()))
    }

    @Test
    fun `prependGreeting handles a document without content`() {
        val result = updater().prependGreeting("""{"type":"doc","version":1}""", "Hallo", "Offene Punkte", listOf("P1"))

        assertTrue(result.contains("Hallo"))
        assertTrue(result.contains("P1"))
        assertTrue(result.contains("bulletList"))
    }
}
