package com.stacklens.adapter.out.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MrSuggestionAdapterDtoTest {
    @Test
    fun `MrSuggestionAdapter_GlDiscussionResponse default and explicit`() {
        val r = GitlabMrSuggestionAdapter.GlDiscussionResponse()
        assertEquals("", r.id)
        assertEquals(emptyList<GitlabMrSuggestionAdapter.GlNote>(), r.notes)
        val r2 =
            GitlabMrSuggestionAdapter.GlDiscussionResponse(
                id = "d1",
                notes = listOf(GitlabMrSuggestionAdapter.GlNote(webUrl = "u")),
            )
        assertEquals("d1", r2.id)
        assertEquals(1, r2.notes.size)
        assertEquals("u", r2.notes[0].webUrl)
    }

    @Test
    fun `MrSuggestionAdapter_GlNote default and explicit`() {
        val n = GitlabMrSuggestionAdapter.GlNote()
        assertNull(n.webUrl)
        val n2 = GitlabMrSuggestionAdapter.GlNote(webUrl = "https://gl/note/1")
        assertEquals("https://gl/note/1", n2.webUrl)
    }

    @Test
    fun `MrSuggestionAdapter_GhCommentResponse default and explicit`() {
        val r = GithubPrSuggestionAdapter.GhCommentResponse()
        assertNull(r.htmlUrl)
        val r2 = GithubPrSuggestionAdapter.GhCommentResponse(htmlUrl = "https://gh/comment/1")
        assertEquals("https://gh/comment/1", r2.htmlUrl)
    }
}
