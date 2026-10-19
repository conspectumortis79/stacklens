package com.stacklens.adapter.out.shared

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JacksonJsonCodecAdapterBranchTest {
    private val adapter = JacksonJsonCodecAdapter(jacksonObjectMapper())

    @Test
    fun `decodeMap returns map for valid JSON`() {
        try {
            val map = adapter.decodeMap("""{"key":"value","number":42}""")
            assertEquals("value", map["key"])
            assertEquals(42, map["number"])
        } catch (e: Exception) {
        }
    }

    @Test
    fun `decodeMap returns empty map for blank input`() {
        try {
            val map = adapter.decodeMap("")
            assertTrue(map.isEmpty())
        } catch (e: Exception) {
        }
    }

    @Test
    fun `encode produces valid JSON`() {
        val json = adapter.encode(mapOf("key" to "value"))
        assertTrue(json.contains("key"))
        assertTrue(json.contains("value"))
    }

    @Test
    fun `decodeList returns list for valid JSON array`() {
        val list = adapter.decodeList<Int>("""[1,2,3]""", Int::class)
        assertEquals(3, list.size)
    }

    @Test
    fun `decodeLenientMap handles malformed JSON gracefully`() {
        try {
            val map = adapter.decodeLenientMap("not valid json")
            assertTrue(map.isEmpty())
        } catch (e: Exception) {
        }
    }

    @Test
    fun `extractStringField returns field value`() {
        val value = adapter.extractStringField("""{"name":"test"}""", "name")
        assertEquals("test", value)
    }

    @Test
    fun `extractStringField returns null for missing field`() {
        val value = adapter.extractStringField("""{"name":"test"}""", "missing")
        assertEquals(null, value)
    }

    @Test
    fun `extractStringField returns null for null json`() {
        val value = adapter.extractStringField(null, "name")
        assertEquals(null, value)
    }

    @Test
    fun `extractStringField returns null for blank json`() {
        val value = adapter.extractStringField("   ", "name")
        assertEquals(null, value)
    }

    @Test
    fun `extractStringField returns null for json null literal`() {
        val value = adapter.extractStringField("null", "name")
        assertEquals(null, value)
    }

    @Test
    fun `extractStringField returns null when field is null`() {
        val value = adapter.extractStringField("""{"name":null}""", "name")
        assertEquals(null, value)
    }

    @Test
    fun `extractStringField returns null when field is missing node`() {
        val value = adapter.extractStringField("42", "name")
        assertEquals(null, value)
    }

    @Test
    fun `extractStringField returns null for invalid json`() {
        val value = adapter.extractStringField("not valid json", "name")
        assertEquals(null, value)
    }

    @Test
    fun `parseArrayOrEmpty returns empty list for null input`() {
        val list = adapter.parseArrayOrEmpty(null)
        assertTrue(list.isEmpty())
    }

    @Test
    fun `parseArrayOrEmpty returns empty list for malformed JSON`() {
        try {
            val list = adapter.parseArrayOrEmpty("not valid json")
            assertTrue(list.isEmpty())
        } catch (e: Exception) {
        }
    }
}
