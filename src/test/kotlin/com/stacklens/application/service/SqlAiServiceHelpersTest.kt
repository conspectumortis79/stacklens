package com.stacklens.application.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SqlAiServiceHelpersTest {
    private val service =
        SqlAiService(
            copilotCliService = org.mockito.kotlin.mock(),
            promptLoader = org.mockito.kotlin.mock(),
            jsonCodec = org.mockito.kotlin.mock(),
            aiJsonExtractor =
                com.stacklens.adapter.out.textfilter
                    .DefaultAiResponseJsonExtractor(),
        )

    @Test
    fun `asText returns empty string for null`() {
        assertEquals("", service.asText(null))
    }

    @Test
    fun `asText returns the string itself when it is a String`() {
        assertEquals("hello", service.asText("hello"))
    }

    @Test
    fun `asText converts non-string to its toString representation`() {
        assertEquals("42", service.asText(42))
        assertEquals("true", service.asText(true))
    }

    @Test
    fun `asList returns the list when input is a List`() {
        val input = listOf("a", "b", "c")
        assertEquals(input, service.asList(input))
    }

    @Test
    fun `asList returns empty list when input is not a List`() {
        assertTrue(service.asList(null).isEmpty())
        assertTrue(service.asList("not a list").isEmpty())
        assertTrue(service.asList(42).isEmpty())
        assertTrue(service.asList(mapOf("k" to "v")).isEmpty())
    }

    @Test
    fun `asMap returns the map when input is a Map`() {
        val input = mapOf<String, Any?>("key" to "value")
        assertEquals(input, service.asMap(input))
    }

    @Test
    fun `asMap returns empty map when input is not a Map`() {
        assertTrue(service.asMap(null).isEmpty())
        assertTrue(service.asMap("not a map").isEmpty())
        assertTrue(service.asMap(42).isEmpty())
        assertTrue(service.asMap(listOf("a")).isEmpty())
    }
}
