package com.stacklens.adapter.out.github

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GithubSearchAdapterDtoTest {
    @Test
    fun `TextMatch default and explicit`() {
        assertEquals("", GithubSearchAdapter.TextMatch().fragment)
        assertEquals("frag", GithubSearchAdapter.TextMatch(fragment = "frag").fragment)
    }

    @Test
    fun `CodeItem default and explicit`() {
        val r = GithubSearchAdapter.CodeItem()
        assertEquals("", r.path)
        assertEquals("", r.htmlUrl)
        assertEquals(emptyList<GithubSearchAdapter.TextMatch>(), r.textMatches)
        val r2 =
            GithubSearchAdapter.CodeItem(
                path = "p",
                htmlUrl = "u",
                textMatches = listOf(GithubSearchAdapter.TextMatch(fragment = "f")),
            )
        assertEquals("p", r2.path)
        assertEquals("u", r2.htmlUrl)
        assertTrue(r2.textMatches.isNotEmpty())
    }

    @Test
    fun `CodeSearchResponse default and explicit`() {
        val r = GithubSearchAdapter.CodeSearchResponse()
        assertEquals(0, r.totalCount)
        assertEquals(emptyList<GithubSearchAdapter.CodeItem>(), r.items)
        val r2 =
            GithubSearchAdapter.CodeSearchResponse(
                totalCount = 5,
                items = listOf(GithubSearchAdapter.CodeItem()),
            )
        assertEquals(5, r2.totalCount)
        assertEquals(1, r2.items.size)
    }

    @Test
    fun `GithubTreeEntryDto explicit constructor`() {
        val r = GithubTreeEntryDto(path = "p", type = "blob")
        assertEquals("p", r.path)
        assertEquals("blob", r.type)
    }
}
