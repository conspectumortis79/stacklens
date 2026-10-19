package com.stacklens.adapter.out.github

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GithubSearchAdapterDataClassesTest {
    @Test
    fun `TextMatch stores fragment with default empty string`() {
        val t = GithubSearchAdapter.TextMatch()
        assertEquals("", t.fragment)
    }

    @Test
    fun `TextMatch stores fragment value`() {
        val t = GithubSearchAdapter.TextMatch(fragment = "matched text")
        assertEquals("matched text", t.fragment)
    }

    @Test
    fun `CodeItem stores all fields with defaults`() {
        val c = GithubSearchAdapter.CodeItem()
        assertEquals("", c.path)
        assertEquals("", c.htmlUrl)
        assertTrue(c.textMatches.isEmpty())
    }

    @Test
    fun `CodeItem stores populated fields`() {
        val c =
            GithubSearchAdapter.CodeItem(
                path = "src/Foo.kt",
                htmlUrl = "https://github.com/x/y/blob/main/src/Foo.kt",
                textMatches = listOf(GithubSearchAdapter.TextMatch("class Foo")),
            )
        assertEquals("src/Foo.kt", c.path)
        assertEquals("https://github.com/x/y/blob/main/src/Foo.kt", c.htmlUrl)
        assertEquals(1, c.textMatches.size)
        assertEquals("class Foo", c.textMatches[0].fragment)
    }

    @Test
    fun `CodeSearchResponse stores fields with defaults`() {
        val r = GithubSearchAdapter.CodeSearchResponse()
        assertEquals(0, r.totalCount)
        assertTrue(r.items.isEmpty())
    }

    @Test
    fun `CodeSearchResponse stores populated fields`() {
        val r =
            GithubSearchAdapter.CodeSearchResponse(
                totalCount = 5,
                items = listOf(GithubSearchAdapter.CodeItem(path = "src/A.kt")),
            )
        assertEquals(5, r.totalCount)
        assertEquals(1, r.items.size)
        assertEquals("src/A.kt", r.items[0].path)
    }
}
