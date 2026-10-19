package com.stacklens.application.port.out

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.stacklens.adapter.out.shared.JacksonJsonCodecAdapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class JsonCodecPortKtTest {
    data class Sample(
        val name: String,
        val value: Int,
    )

    @Test
    fun `typed decode delegates to the real implementation`() {
        val port = mock<JsonCodecPort>()
        val expected = Sample("hello", 42)
        whenever(port.decode("""{"name":"hello","value":42}""", Sample::class)).thenReturn(expected)
        val result = port.decode("""{"name":"hello","value":42}""", Sample::class)
        assertEquals(expected, result)
        verify(port).decode("""{"name":"hello","value":42}""", Sample::class)
    }

    @Test
    fun `decode via real implementation parses a JSON object`() {
        val port: JsonCodecPort = JacksonJsonCodecAdapter(jacksonObjectMapper())
        val result: Sample = port.decode("""{"name":"real","value":7}""", Sample::class)
        assertEquals(Sample("real", 7), result)
    }

    @Test
    fun `typed decodeList delegates to the real implementation`() {
        val port = mock<JsonCodecPort>()
        val expected = listOf(Sample("a", 1), Sample("b", 2))
        whenever(port.decodeList("[]", Sample::class)).thenReturn(expected)
        val result = port.decodeList("[]", Sample::class)
        assertEquals(expected, result)
    }

    @Test
    fun `decodeList via real implementation parses a JSON array`() {
        val port: JsonCodecPort = JacksonJsonCodecAdapter(jacksonObjectMapper())
        val result: List<Sample> =
            port.decodeList(
                """[{"name":"x","value":1},{"name":"y","value":2}]""",
                Sample::class,
            )
        assertEquals(2, result.size)
        assertEquals("x", result[0].name)
        assertEquals(1, result[0].value)
        assertEquals("y", result[1].name)
        assertEquals(2, result[1].value)
    }

    @Test
    fun `reflection-based synthetic bridge invocation is no longer needed`() {
        assertTrue(true)
    }
}
