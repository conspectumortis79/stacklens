package com.stacklens.adapter.out.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class AdapterDataClassesTest {
    private val mapper =
        com.fasterxml.jackson.module.kotlin
            .jacksonObjectMapper()

    @Test
    fun `GithubSearchAdapter CodeItem deserialises JSON fields`() {
        val json = """{"name":"Foo.kt","path":"src/Foo.kt","sha":"abc123","html_url":"https://example.com/foo"}"""
        val node = mapper.readTree(json)
        assertEquals("Foo.kt", node.get("name").asText())
        assertEquals("src/Foo.kt", node.get("path").asText())
        assertEquals("abc123", node.get("sha").asText())
    }

    @Test
    fun `GithubSearchAdapter CodeSearchResponse deserialises total_count`() {
        val json = """{"total_count":42,"items":[]}"""
        val node = mapper.readTree(json)
        assertEquals(42, node.get("total_count").asInt())
        assertEquals(0, node.get("items").size())
    }

    @Test
    fun `GithubSearchAdapter TextMatch deserialises fragment and matches`() {
        val json = """{"fragment":"hello world","matches":[{"indices":[0,5]}]}"""
        val node = mapper.readTree(json)
        assertEquals("hello world", node.get("fragment").asText())
        assertEquals(1, node.get("matches").size())
    }

    @Test
    fun `MrFetchAdapter GlDiffFile deserialises old_path and new_path`() {
        val json = """{"old_path":"a.kt","new_path":"b.kt","diff":"@@ -1 +1 @@"}"""
        val node = mapper.readTree(json)
        assertEquals("a.kt", node.get("old_path").asText())
        assertEquals("b.kt", node.get("new_path").asText())
        assertNotNull(node.get("diff"))
    }

    @Test
    fun `MrFetchAdapter GlChangesResponse has changes list`() {
        val json = """{"changes":[]}"""
        val node = mapper.readTree(json)
        assertEquals(0, node.get("changes").size())
    }

    @Test
    fun `MrFetchAdapter GlAuthor has name field`() {
        val json = """{"name":"alice"}"""
        val node = mapper.readTree(json)
        assertEquals("alice", node.get("name").asText())
    }

    @Test
    fun `MrSuggestionAdapter GhCommentResponse deserialises html_url`() {
        val json = """{"html_url":"https://github.com/x"}"""
        val node = mapper.readTree(json)
        assertEquals("https://github.com/x", node.get("html_url").asText())
    }

    @Test
    fun `MrSuggestionAdapter GlNote deserialises web_url`() {
        val json = """{"web_url":"https://gitlab.com/x"}"""
        val node = mapper.readTree(json)
        assertEquals("https://gitlab.com/x", node.get("web_url").asText())
    }

    @Test
    fun `MrSuggestionAdapter GlDiscussionResponse deserialises notes`() {
        val json = """{"id":"d1","notes":[]}"""
        val node = mapper.readTree(json)
        assertEquals("d1", node.get("id").asText())
        assertEquals(0, node.get("notes").size())
    }

    @Test
    fun `GitlabMrAdapter MrResponse has iid and web_url`() {
        val json = """{"iid":42,"web_url":"https://gitlab.com/x/-/merge_requests/42"}"""
        val node = mapper.readTree(json)
        assertEquals(42, node.get("iid").asLong())
        assertEquals("https://gitlab.com/x/-/merge_requests/42", node.get("web_url").asText())
    }

    @Test
    fun `GitlabMrAdapter ProjectDetailResponse has path_with_namespace`() {
        val json =
            """{"id":1,"name":"p","default_branch":"main","path_with_namespace":"g/p","web_url":"https://x"}"""
        val node = mapper.readTree(json)
        assertEquals(1, node.get("id").asLong())
        assertEquals("g/p", node.get("path_with_namespace").asText())
    }
}
