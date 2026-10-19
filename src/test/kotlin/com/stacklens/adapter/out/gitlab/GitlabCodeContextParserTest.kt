package com.stacklens.adapter.out.gitlab

import com.stacklens.domain.enums.ResultType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GitlabCodeContextParserTest {
    private val parser = GitlabCodeContextParser()

    @Test
    fun `parseCodeContext detects a Kotlin class`() {
        val code = "class MyClass"
        val ctx = parser.parseCodeContext(code)
        assertEquals("MyClass", ctx.className)
        assertNull(ctx.functionName)
        assertEquals(ResultType.CLASS, ctx.resultType)
    }

    @Test
    fun `parseCodeContext detects a Kotlin data class`() {
        val code = "data class UserDto(val name: String)"
        val ctx = parser.parseCodeContext(code)
        assertEquals("UserDto", ctx.className)
        assertEquals(ResultType.CLASS, ctx.resultType)
    }

    @Test
    fun `parseCodeContext detects a Kotlin object`() {
        val code = "object MySingleton"
        val ctx = parser.parseCodeContext(code)
        assertEquals("MySingleton", ctx.className)
        assertEquals(ResultType.CLASS, ctx.resultType)
    }

    @Test
    fun `parseCodeContext detects a Kotlin interface`() {
        val code = "interface MyRepo"
        val ctx = parser.parseCodeContext(code)
        assertEquals("MyRepo", ctx.className)
        assertEquals(ResultType.CLASS, ctx.resultType)
    }

    @Test
    fun `parseCodeContext detects a Kotlin enum class`() {
        val code = "enum class Color"
        val ctx = parser.parseCodeContext(code)
        assertEquals("Color", ctx.className)
        assertEquals(ResultType.CLASS, ctx.resultType)
    }

    @Test
    fun `parseCodeContext detects a Kotlin function`() {
        val code = "fun myFunc() = 42"
        val ctx = parser.parseCodeContext(code)
        assertEquals("myFunc", ctx.functionName)
        assertEquals(ResultType.FUNCTION, ctx.resultType)
    }

    @Test
    fun `parseCodeContext detects a Java method`() {
        val code = "public void myMethod() { }"
        val ctx = parser.parseCodeContext(code)
        assertEquals("myMethod", ctx.functionName)
        assertEquals(ResultType.FUNCTION, ctx.resultType)
    }

    @Test
    fun `parseCodeContext detects a Python def`() {
        val code = "def my_function():"
        val ctx = parser.parseCodeContext(code)
        assertEquals("my_function", ctx.functionName)
        assertEquals(ResultType.FUNCTION, ctx.resultType)
    }

    @Test
    fun `parseCodeContext detects a PHP function`() {
        val code = "function myPhpFunc() { }"
        val ctx = parser.parseCodeContext(code)
        assertEquals("myPhpFunc", ctx.functionName)
        assertEquals(ResultType.FUNCTION, ctx.resultType)
    }

    @Test
    fun `parseCodeContext returns FILE result type when neither class nor function is present`() {
        val code = "// just a comment\nval x = 1"
        val ctx = parser.parseCodeContext(code)
        assertNull(ctx.className)
        assertNull(ctx.functionName)
        assertEquals(ResultType.FILE, ctx.resultType)
    }

    @Test
    fun `parseCodeContext prefers FUNCTION when both class and function are present`() {
        val code =
            """
            class MyClass {
                fun myMethod() = 42
            }
            """.trimIndent()
        val ctx = parser.parseCodeContext(code)
        assertEquals("MyClass", ctx.className)
        assertEquals("myMethod", ctx.functionName)
        assertEquals(ResultType.FUNCTION, ctx.resultType)
    }

    @Test
    fun `parseCodeContext returns last match when multiple declarations exist`() {
        val code =
            """
            class First
            class Second
            """.trimIndent()
        val ctx = parser.parseCodeContext(code)
        assertEquals("Second", ctx.className)
    }

    @Test
    fun `findKeywordInContent returns matching lines with line numbers`() {
        val content = "line 1\nthis has keyword\nline 3\nanother keyword here"
        val matches = parser.findKeywordInContent(content, "keyword")
        assertEquals(2, matches.size)
        assertEquals(2, matches[0].first)
        assertEquals(4, matches[1].first)
    }

    @Test
    fun `findKeywordInContent returns empty list when keyword not found`() {
        val content = "line 1\nline 2\nline 3"
        val matches = parser.findKeywordInContent(content, "missing")
        assertTrue(matches.isEmpty())
    }

    @Test
    fun `findKeywordInContent is case-insensitive`() {
        val content = "Foo\nFOO\nfoo\nFoO"
        val matches = parser.findKeywordInContent(content, "foo")
        assertEquals(4, matches.size)
    }
}
