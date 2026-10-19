package com.stacklens.adapter.out.filesystem

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.stacklens.adapter.out.shared.JacksonJsonCodecAdapter
import com.stacklens.application.port.out.TranslationCatalogPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.core.io.Resource

class ClasspathTranslationCatalogAdapterTest {
    private val mapper: ObjectMapper = jacksonObjectMapper()
    private val codec = JacksonJsonCodecAdapter(mapper)

    private class StaticResourceLoader(
        private val resource: Resource,
    ) : DefaultResourceLoader() {
        override fun getResource(location: String): Resource = resource
    }

    @Test
    fun `load returns empty map when the classpath resource does not exist`() {
        val missing =
            object : ByteArrayResource(ByteArray(0)) {
                override fun exists(): Boolean = false
            }
        val adapter = ClasspathTranslationCatalogAdapter(StaticResourceLoader(missing), codec)
        val map = adapter.load("does-not-exist-${System.nanoTime()}")
        assertTrue(map.isEmpty())
    }

    @Test
    fun `load flattens a nested JSON tree into dot-notation keys`() {
        val json =
            """
            {
              "common": {
                "save": "Save",
                "cancel": "Cancel"
              },
              "title": "Welcome"
            }
            """.trimIndent()
        val adapter =
            ClasspathTranslationCatalogAdapter(StaticResourceLoader(ByteArrayResource(json.toByteArray())), codec)
        val map = adapter.load("en")
        assertEquals("Save", map["common.save"])
        assertEquals("Cancel", map["common.cancel"])
        assertEquals("Welcome", map["title"])
    }

    @Test
    fun `load ignores non-string leaves (numbers, booleans, lists)`() {
        val json =
            """
            {
              "label": "OK",
              "count": 42,
              "flag": true,
              "items": ["a", "b"]
            }
            """.trimIndent()
        val adapter =
            ClasspathTranslationCatalogAdapter(StaticResourceLoader(ByteArrayResource(json.toByteArray())), codec)
        val map = adapter.load("en")
        assertEquals("OK", map["label"])
        assertEquals(null, map["count"])
        assertEquals(null, map["flag"])
        assertEquals(null, map["items"])
    }

    @Test
    fun `load returns empty map when the JSON cannot be parsed`() {
        val adapter =
            ClasspathTranslationCatalogAdapter(
                StaticResourceLoader(ByteArrayResource("not json".toByteArray())),
                codec,
            )
        val map = adapter.load("en")
        assertTrue(map.isEmpty())
    }

    @Test
    fun `load flattens deeply nested structures`() {
        val json = """{"a":{"b":{"c":"deep"}}}"""
        val adapter =
            ClasspathTranslationCatalogAdapter(StaticResourceLoader(ByteArrayResource(json.toByteArray())), codec)
        val map = adapter.load("en")
        assertEquals("deep", map["a.b.c"])
    }

    @Test
    fun `load handles an empty JSON object`() {
        val adapter =
            ClasspathTranslationCatalogAdapter(StaticResourceLoader(ByteArrayResource("{}".toByteArray())), codec)
        val map = adapter.load("en")
        assertTrue(map.isEmpty())
    }

    @Test
    fun `adapts to TranslationCatalogPort`() {
        val adapter: TranslationCatalogPort =
            ClasspathTranslationCatalogAdapter(
                StaticResourceLoader(
                    object : ByteArrayResource(ByteArray(0)) {
                        override fun exists(): Boolean = false
                    },
                ),
                codec,
            )
        val map = adapter.load("does-not-exist")
        assertTrue(map.isEmpty())
    }
}
