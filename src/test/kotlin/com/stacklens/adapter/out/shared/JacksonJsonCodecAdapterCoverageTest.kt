package com.stacklens.adapter.out.shared

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.stacklens.application.port.out.JsonTree
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class JacksonJsonCodecAdapterCoverageTest {
    private val adapter = JacksonJsonCodecAdapter(jacksonObjectMapper())

    @Test
    fun `tree decoding ignores missing blank and malformed payloads`() {
        assertNull(adapter.decodeTree(null))
        assertNull(adapter.decodeTree(""))
        assertNull(adapter.decodeTree("   "))
        assertNull(adapter.decodeTree("{ not json"))
    }

    @Test
    fun `every json scalar becomes its own tree node`() {
        assertEquals(JsonTree.NullNode, adapter.decodeTree("null"))
        assertEquals(JsonTree.BooleanNode(true), adapter.decodeTree("true"))
        assertEquals(JsonTree.NumberNode(2.5), adapter.decodeTree("2.5"))
        assertEquals(JsonTree.TextNode("hi"), adapter.decodeTree("\"hi\""))
    }

    @Test
    fun `objects and arrays are mapped recursively`() {
        val tree =
            adapter.decodeTree("""{"list":[1,"two",{"nested":null}]}""")

        val root = tree as JsonTree.ObjectNode
        val list = root.entries.getValue("list") as JsonTree.ArrayNode

        assertEquals(JsonTree.NumberNode(1.0), list.elements[0])
        assertEquals(JsonTree.TextNode("two"), list.elements[1])
        assertEquals(
            JsonTree.ObjectNode(mapOf("nested" to JsonTree.NullNode)),
            list.elements[2],
        )
    }

    @Test
    fun `string extraction tolerates empty and non object payloads`() {
        assertNull(adapter.extractStringField(null, "a"))
        assertNull(adapter.extractStringField("  ", "a"))
        assertNull(adapter.extractStringField("null", "a"))
        assertNull(adapter.extractStringField("[]", "a"))
        assertNull(adapter.extractStringField("""{"a":null}""", "a"))
        assertNull(adapter.extractStringField("""{"other":1}""", "a"))
        assertEquals("5", adapter.extractStringField("""{"a":5}""", "a"))
        assertEquals("text", adapter.extractStringField("""{"a":"text"}""", "a"))
    }

    @Test
    fun `array parsing falls back to an empty list`() {
        assertEquals(emptyList<Any>(), adapter.parseArrayOrEmpty(null))
        assertEquals(emptyList<Any>(), adapter.parseArrayOrEmpty("  "))
        assertEquals(emptyList<Any>(), adapter.parseArrayOrEmpty("null"))
        assertEquals(emptyList<Any>(), adapter.parseArrayOrEmpty("[oops"))
        assertEquals(listOf(1, "b"), adapter.parseArrayOrEmpty("""[1,"b"]"""))
    }

    @Test
    fun `maps can be decoded from text and from streams`() {
        assertEquals(mapOf("a" to 1), adapter.decodeMap("""{"a":1}"""))
        assertEquals(mapOf("b" to "c"), adapter.decodeMap(ByteArrayInputStream("""{"b":"c"}""".toByteArray())))
        assertEquals(mapOf("d" to "true"), adapter.decodeLenientMap("""{"d":"true"}"""))
    }

    @Test
    fun `typed decoding honours the requested class and list shape`() {
        @Suppress("UNCHECKED_CAST")
        val map = adapter.decode("""{"k":"v"}""", Map::class) as Map<String, Any?>

        @Suppress("UNCHECKED_CAST")
        val list = adapter.decodeList("""[{"k":"v"}]""", Map::class) as List<Map<String, Any?>>

        @Suppress("UNCHECKED_CAST")
        val fromStream = adapter.decode(ByteArrayInputStream("""{"k":"stream"}""".toByteArray()), Map::class) as Map<String, Any?>

        assertEquals("v", map.getValue("k"))
        assertEquals(1, list.size)
        assertEquals("stream", fromStream.getValue("k"))
        assertEquals("v", list.single().getValue("k"))
        assertNull(adapter.extractStringField("{broken", "a"))
    }

    @Test
    fun `encoding round trips through the strict mapper`() {
        val encoded = adapter.encode(mapOf("flag" to true, "count" to 3))

        assertTrue(encoded.contains("\"flag\":true"), encoded)
        assertEquals(mapOf("flag" to true, "count" to 3), adapter.decodeMap(encoded))
    }
}
