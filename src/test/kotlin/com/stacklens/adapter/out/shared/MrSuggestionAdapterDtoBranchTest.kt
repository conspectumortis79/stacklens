package com.stacklens.adapter.out.shared

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class MrSuggestionAdapterDtoBranchTest {
    private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

    @Test
    fun `GlDiscussionResponse deserializes id and notes`() {
        val json = """{"id":"d1","notes":[{"web_url":"http://x"}]}"""
        val r = mapper.readValue(json, GitlabMrSuggestionAdapter.GlDiscussionResponse::class.java)
        assertEquals("d1", r.id)
        assertEquals(1, r.notes.size)
        assertEquals("http://x", r.notes[0].webUrl)
    }

    @Test
    fun `GlDiscussionResponse with empty notes list`() {
        val r = mapper.readValue("""{"id":"d2"}""", GitlabMrSuggestionAdapter.GlDiscussionResponse::class.java)
        assertEquals("d2", r.id)
        assertEquals(0, r.notes.size)
    }

    @Test
    fun `GlDiscussionResponse equality holds by field value`() {
        val a = GitlabMrSuggestionAdapter.GlDiscussionResponse(id = "1", notes = emptyList())
        val b = GitlabMrSuggestionAdapter.GlDiscussionResponse(id = "1", notes = emptyList())
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `GlDiscussionResponse per-field equality branches`() {
        val base = GitlabMrSuggestionAdapter.GlDiscussionResponse(id = "1", notes = listOf(GitlabMrSuggestionAdapter.GlNote("http://x")))
        assertNotEquals(base, base.copy(id = "2"))
        val b = base.copy(notes = emptyList())
        assertNotEquals(base, b)
    }

    @Test
    fun `GlNote deserializes webUrl`() {
        val r = mapper.readValue("""{"web_url":"http://note"}""", GitlabMrSuggestionAdapter.GlNote::class.java)
        assertEquals("http://note", r.webUrl)
    }

    @Test
    fun `GlNote with null webUrl`() {
        val r = mapper.readValue("""{}""", GitlabMrSuggestionAdapter.GlNote::class.java)
        assertEquals(null, r.webUrl)
    }

    @Test
    fun `GlNote equality holds by field value`() {
        val a = GitlabMrSuggestionAdapter.GlNote("http://x")
        val b = GitlabMrSuggestionAdapter.GlNote("http://x")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, GitlabMrSuggestionAdapter.GlNote("http://y"))
        assertNotEquals(a, GitlabMrSuggestionAdapter.GlNote(null))
    }

    @Test
    fun `GhCommentResponse deserializes htmlUrl`() {
        val r = mapper.readValue("""{"html_url":"http://comment"}""", GithubPrSuggestionAdapter.GhCommentResponse::class.java)
        assertEquals("http://comment", r.htmlUrl)
    }

    @Test
    fun `GhCommentResponse with null htmlUrl`() {
        val r = mapper.readValue("""{}""", GithubPrSuggestionAdapter.GhCommentResponse::class.java)
        assertEquals(null, r.htmlUrl)
    }

    @Test
    fun `GhCommentResponse equality holds by field value`() {
        val a = GithubPrSuggestionAdapter.GhCommentResponse("http://x")
        val b = GithubPrSuggestionAdapter.GhCommentResponse("http://x")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `PostRequest equality holds by field value`() {
        val a =
            com.stacklens.application.port.out.MrSuggestionPort.PostRequest(
                mrUrl = "http://x",
                platform = "GitLab",
                gitConfigId = 1L,
                gitlabConfigs = emptyList(),
                githubConfigs = emptyList(),
                diffBaseSha = "b",
                diffStartSha = "s",
                diffHeadSha = "h",
                filePath = "src/F.kt",
                lineNumber = 10,
                suggestedCode = "fix",
            )
        val b = a.copy()
        assertEquals(a, b)
    }
}
