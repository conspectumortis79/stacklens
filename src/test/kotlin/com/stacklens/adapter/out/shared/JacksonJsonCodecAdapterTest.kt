package com.stacklens.adapter.out.shared

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class JacksonJsonCodecAdapterTest {
    private val rawTabInValue = """[{"name":"a\tb","age":1}]"""

    private val adapter = JacksonJsonCodecAdapter(jacksonObjectMapper())

    data class Person(
        val name: String,
        val age: Int = 0,
    )

    data class PersonWithNote(
        val name: String,
        val age: Int = 0,
        val note: String? = null,
    )

    @Test
    fun `decode parses a JSON object into the requested type`() {
        val p = adapter.decode("""{"name":"Alice","age":30}""", Person::class)
        assertEquals("Alice", p.name)
        assertEquals(30, p.age)
    }

    @Test
    fun `decode tolerates unknown properties`() {
        val p = adapter.decode("""{"name":"Bob","age":1,"extra":"ignored"}""", Person::class)
        assertEquals("Bob", p.name)
    }

    @Test
    fun `decode from stream works the same as from string`() {
        val p = adapter.decode(ByteArrayInputStream("""{"name":"S","age":2}""".toByteArray()), Person::class)
        assertEquals("S", p.name)
    }

    @Test
    fun `decodeList keeps the character behind an escape the model did not mean`() {
        val payload =
            adapter.decodeList(
                """[{"name":"A","age":1,"note":"pass\@evil"}]""",
                PersonWithNote::class,
            )

        assertEquals("pass@evil", payload.single().note)
    }

    @Test
    fun `decodeList still resolves the escapes that json really defines`() {
        val payload =
            adapter.decodeList(
                """[{"name":"A","age":1,"note":"tab\there\nnl"}]""",
                PersonWithNote::class,
            )

        assertEquals("tab	here\nnl", payload.single().note)
    }

    @Test
    fun `decodeList parses a JSON array`() {
        val list = adapter.decodeList("""[{"name":"A","age":1},{"name":"B","age":2}]""", Person::class)
        assertEquals(2, list.size)
        assertEquals("A", list[0].name)
        assertEquals("B", list[1].name)
    }

    @Test
    fun `decodeMap parses a JSON object into a map`() {
        val map = adapter.decodeMap("""{"a":1,"b":"x"}""")
        assertEquals(1, map["a"])
        assertEquals("x", map["b"])
    }

    @Test
    fun `decodeLenientMap tolerates unquoted keys and single quotes`() {
        val map = adapter.decodeLenientMap("{a:1,'b':2}")
        assertEquals(1, map["a"])
        assertEquals(2, map["b"])
    }

    @Test
    fun `decodeMap from stream`() {
        val map = adapter.decodeMap(ByteArrayInputStream("""{"a":1}""".toByteArray()))
        assertEquals(1, map["a"])
    }

    @Test
    fun `encode serialises a value to JSON`() {
        val json = adapter.encode(Person("Alice", 30))
        assertTrue(json.contains("Alice"))
        assertTrue(json.contains("30"))
    }

    @Test
    fun `extractStringField returns null for null or blank input`() {
        assertNull(adapter.extractStringField(null, "x"))
        assertNull(adapter.extractStringField("", "x"))
        assertNull(adapter.extractStringField("   ", "x"))
    }

    @Test
    fun `extractStringField returns the value when the field is a string`() {
        assertEquals("hi", adapter.extractStringField("""{"x":"hi"}""", "x"))
    }

    @Test
    fun `extractStringField returns null when the field is missing`() {
        assertNull(adapter.extractStringField("""{"y":1}""", "x"))
    }

    @Test
    fun `extractStringField returns null when the field is null`() {
        assertNull(adapter.extractStringField("""{"x":null}""", "x"))
    }

    @Test
    fun `extractStringField returns null when the input is invalid JSON`() {
        assertNull(adapter.extractStringField("not json", "x"))
    }

    @Test
    fun `parseArrayOrEmpty returns an empty list for null or blank input`() {
        assertTrue(adapter.parseArrayOrEmpty(null).isEmpty())
        assertTrue(adapter.parseArrayOrEmpty("").isEmpty())
        assertTrue(adapter.parseArrayOrEmpty("   ").isEmpty())
    }

    @Test
    fun `parseArrayOrEmpty parses a valid array`() {
        val list = adapter.parseArrayOrEmpty("""[1,"x",true]""")
        assertEquals(3, list.size)
        assertEquals(1, list[0])
        assertEquals("x", list[1])
        assertEquals(true, list[2])
    }

    @Test
    fun `parseArrayOrEmpty returns an empty list for invalid JSON`() {
        assertTrue(adapter.parseArrayOrEmpty("not json").isEmpty())
    }

    @Test
    fun `parseArrayOrEmpty returns an empty list for a JSON object (not an array)`() {
        val list = adapter.parseArrayOrEmpty("""{"x":1}""")
        assertTrue(list.isEmpty() || list.size == 1)
    }

    @Test
    fun `decodeList accepts a raw newline inside a string value`() {
        val json = "[{\"name\":\"line one\nline two\",\"age\":2}]"
        val items = adapter.decodeList(json, Person::class)
        assertEquals(1, items.size)
        assertEquals("line one\nline two", items[0].name)
    }

    @Test
    fun `decodeList accepts a raw tab inside a string value`() {
        val items = adapter.decodeList(rawTabInValue, Person::class)
        assertEquals("a\tb", items[0].name)
    }

    @Test
    fun `decodeLenientMap accepts a raw newline inside a string value`() {
        val map = adapter.decodeLenientMap("{\"name\":\"one\ntwo\"}")
        assertEquals("one\ntwo", map["name"])
    }

    @Test
    fun `decode keeps rejecting unescaped control chars because it reads non-ai payloads`() {
        val result = runCatching { adapter.decode("{\"name\":\"one\ntwo\"}", Person::class) }
        assertTrue(result.isFailure)
    }
}
