package com.stacklens.application.port.out

import com.stacklens.application.port.out.prompt.PromptContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PromptContextTest {
    @Test
    fun `get returns raw template when key exists`() {
        val ctx = PromptContext(mapOf("greeting" to "Hello World"))
        assertEquals("Hello World", ctx.get("greeting"))
    }

    @Test
    fun `get returns key string when key does not exist`() {
        val ctx = PromptContext(emptyMap())
        assertEquals("missingKey", ctx.get("missingKey"))
    }

    @Test
    fun `get performs single variable substitution`() {
        val ctx = PromptContext(mapOf("welcome" to "Hello {name}"))
        assertEquals("Hello Alice", ctx.get("welcome", mapOf("name" to "Alice")))
    }

    @Test
    fun `get performs multiple variable substitutions`() {
        val ctx = PromptContext(mapOf("greeting" to "{greeting} {name}, welcome to {place}"))
        assertEquals(
            "Hello Alice, welcome to Wonderland",
            ctx.get(
                "greeting",
                mapOf(
                    "greeting" to "Hello",
                    "name" to "Alice",
                    "place" to "Wonderland",
                ),
            ),
        )
    }

    @Test
    fun `get replaces same placeholder multiple times`() {
        val ctx = PromptContext(mapOf("repeat" to "{word} {word} {word}"))
        assertEquals("test test test", ctx.get("repeat", mapOf("word" to "test")))
    }

    @Test
    fun `get handles empty vars map`() {
        val ctx = PromptContext(mapOf("template" to "Hello {name}"))
        assertEquals("Hello {name}", ctx.get("template", emptyMap()))
    }

    @Test
    fun `get handles vars with no placeholders in template`() {
        val ctx = PromptContext(mapOf("static" to "No variables here"))
        assertEquals("No variables here", ctx.get("static", mapOf("extra" to "ignored")))
    }

    @Test
    fun `get returns empty string when key maps to empty value`() {
        val ctx = PromptContext(mapOf("key" to ""))
        assertEquals("", ctx.get("key"))
    }

    @Test
    fun `get leaves unknown placeholders unchanged`() {
        val ctx = PromptContext(mapOf("template" to "Hello {name} from {city}"))
        assertEquals("Hello Alice from {city}", ctx.get("template", mapOf("name" to "Alice")))
    }

    @Test
    fun `get falls back to key when template is missing from map`() {
        val ctx = PromptContext(emptyMap())
        assertEquals("missingKey", ctx.get("missingKey"))
    }
}
