package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AiTestHelpersTest {
    private val mapper = ObjectMapper()

    @Test
    fun `extractStringIds reads ids from JsonNode array (ObjectNode items)`() {
        val json = """{"data":[{"id":"openai/gpt-4o","name":"GPT-4o"},{"id":"anthropic/claude-3.5","name":"Claude"}]}"""
        val dataNode = mapper.readTree(json).get("data")
        val list = AiTestHelpers.nodeAsList(dataNode)
        val ids = AiTestHelpers.extractStringIds(list, "id").sorted()
        assertEquals(listOf("anthropic/claude-3.5", "openai/gpt-4o"), ids)
    }

    @Test
    fun `extractStringIds reads ids from Map list (legacy path)`() {
        val list: List<Map<String, Any>> =
            listOf(
                mapOf("id" to "openai/gpt-4o"),
                mapOf("id" to "anthropic/claude-3.5"),
            )
        val ids = AiTestHelpers.extractStringIds(list, "id").sorted()
        assertEquals(listOf("anthropic/claude-3.5", "openai/gpt-4o"), ids)
    }

    @Test
    fun `extractStringIds handles large OpenRouter response (regression for modelCount=0)`() {
        val sb = StringBuilder("""{"data":[""")
        repeat(414) { i ->
            if (i > 0) sb.append(',')
            sb.append("""{"id":"model-$i","name":"Model $i"}""")
        }
        sb.append("]}")
        val dataNode = mapper.readTree(sb.toString()).get("data")
        val list = AiTestHelpers.nodeAsList(dataNode)
        val ids = AiTestHelpers.extractStringIds(list, "id")
        assertEquals(414, ids.size, "expected 414 model ids, got ${ids.size}")
        assertTrue(ids.contains("model-0"))
        assertTrue(ids.contains("model-413"))
    }

    @Test
    fun `extractStringIds returns empty list when dataList is null`() {
        assertEquals(emptyList<String>(), AiTestHelpers.extractStringIds(null, "id"))
    }

    @Test
    fun `extractStringIds skips items of unsupported type`() {
        val list: List<Any> = listOf(42, "string-item", listOf(1, 2))
        val ids = AiTestHelpers.extractStringIds(list, "id")
        assertTrue(ids.isEmpty())
    }

    @Test
    fun `extractStringIds converts non-string non-JsonNode values to toString`() {
        val list: List<Map<String, Any>> = listOf(mapOf("id" to 42))
        val ids = AiTestHelpers.extractStringIds(list, "id")
        assertEquals(listOf("42"), ids)
    }

    @Test
    fun `extractStringIds returns asText for textual JsonNode values`() {
        val json = """{"data":[{"id":"hello"}]}"""
        val dataNode = mapper.readTree(json).get("data")
        val list = AiTestHelpers.nodeAsList(dataNode)
        val ids = AiTestHelpers.extractStringIds(list, "id")
        assertEquals(listOf("hello"), ids)
    }

    @Test
    fun `extractStringIds skips non-textual JsonNode values`() {
        val dataNode = mapper.readTree("""{"data":[{"id":42},{"id":"ok"}]}""").get("data")
        val list = AiTestHelpers.nodeAsList(dataNode)
        assertEquals(listOf("ok"), AiTestHelpers.extractStringIds(list, "id"))
    }

    @Test
    fun `extractStringIds skips blank string values`() {
        val list: List<Map<String, Any>> = listOf(mapOf("id" to "   "))
        val ids = AiTestHelpers.extractStringIds(list, "id")
        assertTrue(ids.isEmpty())
    }

    @Test
    fun `extractStringIds skips items missing the key`() {
        val json = """{"data":[{"id":"a"},{"name":"no-id"},{"id":"b"}]}"""
        val dataNode = mapper.readTree(json).get("data")
        val list = AiTestHelpers.nodeAsList(dataNode)
        assertEquals(listOf("a", "b"), AiTestHelpers.extractStringIds(list, "id"))
    }

    @Test
    fun `nodeAsList returns null for non-array nodes`() {
        val node = mapper.readTree("""{"data":{"foo":"bar"}}""")
        assertEquals(null, AiTestHelpers.nodeAsList(node.get("data")))
        assertEquals(null, AiTestHelpers.nodeAsList(null))
    }

    @Test
    fun `nodeAsList returns list of ObjectNode elements for array`() {
        val node = mapper.readTree("""{"data":[{"id":"a"},{"id":"b"}]}""")
        val list = AiTestHelpers.nodeAsList(node.get("data"))
        assertEquals(2, list?.size)
        assertTrue(list?.get(0) is JsonNode)
    }
}
