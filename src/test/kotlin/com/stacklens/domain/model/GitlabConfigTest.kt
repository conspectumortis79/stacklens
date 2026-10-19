package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GitlabConfigTest {
    @Test
    fun `projectSelectorList splits comma-separated project ids`() {
        val config =
            GitlabConfig(name = "test", baseUrl = "https://gitlab.example.com", accessToken = "tok", projectIds = " 100 , 200 , 300 ")
        assertEquals(listOf("100", "200", "300"), config.projectSelectorList())
    }

    @Test
    fun `projectSelectorList returns empty list when projectIds is null`() {
        val config = GitlabConfig(name = "test", baseUrl = "https://gitlab.example.com", accessToken = "tok", projectIds = null)
        assertTrue(config.projectSelectorList().isEmpty())
    }

    @Test
    fun `projectSelectorList filters blank entries`() {
        val config = GitlabConfig(name = "test", baseUrl = "https://gitlab.example.com", accessToken = "tok", projectIds = "1,,  ,2")
        assertEquals(listOf("1", "2"), config.projectSelectorList())
    }

    @Test
    fun `projectIdList parses valid numeric ids`() {
        val config = GitlabConfig(name = "test", baseUrl = "https://gitlab.example.com", accessToken = "tok", projectIds = "10,20,30")
        assertEquals(listOf(10L, 20L, 30L), config.projectIdList())
    }

    @Test
    fun `projectIdList skips non-numeric entries`() {
        val config = GitlabConfig(name = "test", baseUrl = "https://gitlab.example.com", accessToken = "tok", projectIds = "10,foo,30")
        assertEquals(listOf(10L, 30L), config.projectIdList())
    }

    @Test
    fun `projectIdList returns empty list when projectIds is null`() {
        val config = GitlabConfig(name = "test", baseUrl = "https://gitlab.example.com", accessToken = "tok")
        assertTrue(config.projectIdList().isEmpty())
    }

    @Test
    fun `resolvedUrls returns baseUrl and null when no path segment present`() {
        val config = GitlabConfig(name = "n", baseUrl = "https://gitlab.example.com", accessToken = "tok")
        val (apiBase, group) = config.resolvedUrls()
        assertEquals("https://gitlab.example.com", apiBase)
        assertNull(group)
    }

    @Test
    fun `resolvedUrls strips trailing slash from baseUrl`() {
        val config = GitlabConfig(name = "n", baseUrl = "https://gitlab.example.com/", accessToken = "tok")
        val (apiBase, _) = config.resolvedUrls()
        assertEquals("https://gitlab.example.com", apiBase)
    }

    @Test
    fun `resolvedUrls extracts single-segment path as group`() {
        val config = GitlabConfig(name = "n", baseUrl = "https://gitlab.example.com/fulfillment", accessToken = "tok")
        val (apiBase, group) = config.resolvedUrls()
        assertEquals("https://gitlab.example.com", apiBase)
        assertEquals("fulfillment", group)
    }

    @Test
    fun `resolvedUrls extracts multi-segment path as group`() {
        val config = GitlabConfig(name = "n", baseUrl = "https://gitlab.example.com/a/b", accessToken = "tok")
        val (apiBase, group) = config.resolvedUrls()
        assertEquals("https://gitlab.example.com", apiBase)
        assertEquals("a/b", group)
    }

    @Test
    fun `resolvedUrls explicit groupId takes precedence over URL path`() {
        val config =
            GitlabConfig(name = "n", baseUrl = "https://gitlab.example.com/path-group", accessToken = "tok", groupId = "explicit-group")
        val (apiBase, group) = config.resolvedUrls()
        assertEquals("https://gitlab.example.com", apiBase)
        assertEquals("explicit-group", group)
    }

    @Test
    fun `resolvedUrls blank groupId falls back to URL path`() {
        val config = GitlabConfig(name = "n", baseUrl = "https://gitlab.example.com/from-url", accessToken = "tok", groupId = "  ")
        val (apiBase, group) = config.resolvedUrls()
        assertEquals("https://gitlab.example.com", apiBase)
        assertEquals("from-url", group)
    }

    @Test
    fun `resolvedUrls falls back gracefully on malformed URL`() {
        val config = GitlabConfig(name = "n", baseUrl = "not-a-valid-url", accessToken = "tok")
        val (apiBase, group) = config.resolvedUrls()
        assertEquals("null://null", apiBase)
        assertEquals("not-a-valid-url", group)
    }

    @Test
    fun `resolvedUrls null groupId and no path returns null group`() {
        val config = GitlabConfig(name = "n", baseUrl = "https://gitlab.example.com", accessToken = "tok", groupId = null)
        val (_, group) = config.resolvedUrls()
        assertNull(group)
    }

    @Test
    fun `resolvedUrls falls back to catch block when URI constructor throws`() {
        val config = GitlabConfig(name = "n", baseUrl = "ht tp://bad url", accessToken = "tok", groupId = null)
        val (apiBase, group) = config.resolvedUrls()
        assertEquals("ht tp://bad url", apiBase)
        assertNull(group)
    }

    @Test
    fun `resolvedUrls catch block with non-blank groupId`() {
        val config = GitlabConfig(name = "n", baseUrl = "ht tp://bad url", accessToken = "tok", groupId = "mygroup")
        val (apiBase, group) = config.resolvedUrls()
        assertEquals("ht tp://bad url", apiBase)
        assertEquals("mygroup", group)
    }

    @Test
    fun `resolvedUrls catch block with blank groupId returns null`() {
        val config = GitlabConfig(name = "n", baseUrl = "ht tp://bad url", accessToken = "tok", groupId = "  ")
        val (apiBase, group) = config.resolvedUrls()
        assertEquals("ht tp://bad url", apiBase)
        assertNull(group)
    }

    @Test
    fun `resolvedUrls handles opaque URI where path is null`() {
        val config = GitlabConfig(name = "n", baseUrl = "urn:isbn:0451450523", accessToken = "tok")
        val (apiBase, group) = config.resolvedUrls()
        assertEquals("urn://null", apiBase)
        assertNull(group)
    }
}
