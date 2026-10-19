package com.stacklens.adapter.out.confluence

import com.stacklens.domain.model.ConfluenceConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConfluenceClientTest {
    private fun client() =
        ConfluenceClient(
            connectTimeoutMs = 1000,
            readTimeoutMs = 1000,
        )

    private fun cfg(
        cloudMode: Boolean = true,
        baseUrl: String = "https://example.atlassian.net",
        spaceKeys: String = "",
        searchRootUrl: String? = null,
    ) = ConfluenceConfig(
        name = "test",
        baseUrl = baseUrl,
        username = "u",
        apiToken = "t",
        cloudMode = cloudMode,
        spaceKeys = spaceKeys,
        searchRootUrl = searchRootUrl,
    )

    @Test
    fun `buildPageUrl Cloud builds wiki spaces URL when webui is empty`() {
        val item =
            ConfluenceClient.ContentItem(
                id = "12345",
                space = ConfluenceClient.SpaceRef(key = "DEV", name = "Dev"),
                links = ConfluenceClient.LinkMap(webui = ""),
            )
        val url = client().buildPageUrl(cfg(cloudMode = true), item)
        assertEquals("https://example.atlassian.net/wiki/spaces/DEV/pages/12345", url)
    }

    @Test
    fun `buildPageUrl Cloud appends wiki segment when baseUrl has no wiki path`() {
        val item =
            ConfluenceClient.ContentItem(
                id = "9999",
                space = ConfluenceClient.SpaceRef(key = "DOC", name = "Docs"),
                links = ConfluenceClient.LinkMap(),
            )
        val url = client().buildPageUrl(cfg(cloudMode = true), item)
        assertTrue(url.contains("/wiki/spaces/DOC/pages/9999"))
    }

    @Test
    fun `buildPageUrl Cloud does not double-add wiki segment when baseUrl already has wiki`() {
        val item =
            ConfluenceClient.ContentItem(
                id = "777",
                space = ConfluenceClient.SpaceRef(key = "OPS", name = "Ops"),
                links = ConfluenceClient.LinkMap(),
            )
        val url =
            client().buildPageUrl(cfg(cloudMode = true, baseUrl = "https://example.atlassian.net/wiki"), item)
        assertFalse(url.contains("/wiki/wiki/"))
        assertTrue(url.contains("/wiki/spaces/OPS/pages/777"))
    }

    @Test
    fun `buildPageUrl Cloud uses absolute webui URL when already absolute`() {
        val item =
            ConfluenceClient.ContentItem(
                id = "1",
                space = ConfluenceClient.SpaceRef(key = "X", name = "X"),
                links = ConfluenceClient.LinkMap(webui = "https://other.example.com/path"),
            )
        val url = client().buildPageUrl(cfg(cloudMode = true), item)
        assertEquals("https://other.example.com/path", url)
    }

    @Test
    fun `buildPageUrl Server uses webui directly`() {
        val item =
            ConfluenceClient.ContentItem(
                id = "42",
                space = ConfluenceClient.SpaceRef(key = "DEV", name = "Dev"),
                links = ConfluenceClient.LinkMap(webui = "/spaces/DEV/pages/42/My-Page"),
            )
        val url = client().buildPageUrl(cfg(cloudMode = false), item)
        assertEquals("https://example.atlassian.net/spaces/DEV/pages/42/My-Page", url)
    }

    @Test
    fun `buildPageUrl Server appends empty webui to base URL`() {
        val item =
            ConfluenceClient.ContentItem(
                id = "1",
                space = ConfluenceClient.SpaceRef(key = "X", name = "X"),
                links = ConfluenceClient.LinkMap(webui = ""),
            )
        val url = client().buildPageUrl(cfg(cloudMode = false), item)
        assertEquals("https://example.atlassian.net", url)
    }

    @Test
    fun `matchesSearchRoot returns true when root is null`() {
        assertTrue(client().matchesSearchRoot(null, "https://example.com/page"))
    }

    @Test
    fun `matchesSearchRoot returns true when root is blank`() {
        assertTrue(client().matchesSearchRoot("", "https://example.com/page"))
        assertTrue(client().matchesSearchRoot("   ", "https://example.com/page"))
    }

    @Test
    fun `matchesSearchRoot matches exact page URL`() {
        assertTrue(client().matchesSearchRoot("https://example.com", "https://example.com"))
    }

    @Test
    fun `matchesSearchRoot matches descendant URL`() {
        assertTrue(client().matchesSearchRoot("https://example.com", "https://example.com/page1"))
    }

    @Test
    fun `matchesSearchRoot rejects URL outside root`() {
        assertFalse(client().matchesSearchRoot("https://example.com", "https://other.com/page"))
    }

    @Test
    fun `matchesSearchRoot is case insensitive`() {
        assertTrue(client().matchesSearchRoot("HTTPS://EXAMPLE.COM", "https://example.com/page"))
    }

    @Test
    fun `matchesSearchRoot tolerates trailing slashes on both sides`() {
        assertTrue(client().matchesSearchRoot("https://example.com/", "https://example.com/page"))
    }

    @Test
    fun `matchesAnySearchRoot returns true when list empty`() {
        assertTrue(client().matchesAnySearchRoot(emptyList(), "https://example.com/page"))
    }

    @Test
    fun `matchesAnySearchRoot returns true when any root matches`() {
        assertTrue(
            client().matchesAnySearchRoot(
                listOf("https://a.com", "https://b.com"),
                "https://b.com/page1",
            ),
        )
    }

    @Test
    fun `matchesAnySearchRoot returns false when no root matches`() {
        assertFalse(
            client().matchesAnySearchRoot(
                listOf("https://a.com", "https://b.com"),
                "https://c.com/page1",
            ),
        )
    }

    @Test
    fun `matchesAnySearchRoot skips blank roots`() {
        assertTrue(
            client().matchesAnySearchRoot(
                listOf("", "https://example.com"),
                "https://example.com/page1",
            ),
        )
    }

    @Test
    fun `extractExcerpt uses body view value when present`() {
        val item =
            ConfluenceClient.ContentItem(
                body =
                    ConfluenceClient.BodyMap(
                        view = ConfluenceClient.BodyContent(value = "<p>Some text containing Keyword here</p>"),
                    ),
            )
        val excerpt = client().extractExcerpt(item, "Keyword")
        assertTrue(excerpt.contains("Keyword"))
        assertFalse(excerpt.contains("<p>"))
    }

    @Test
    fun `extractExcerpt falls back to storage when view is null`() {
        val item =
            ConfluenceClient.ContentItem(
                body =
                    ConfluenceClient.BodyMap(
                        storage = ConfluenceClient.BodyContent(value = "<p>Storage body with Keyword</p>"),
                    ),
            )
        val excerpt = client().extractExcerpt(item, "Keyword")
        assertTrue(excerpt.contains("Keyword"))
    }

    @Test
    fun `extractExcerpt falls back to excerpt field when body missing`() {
        val item = ConfluenceClient.ContentItem(excerpt = "excerpt-with-Keyword")
        val excerpt = client().extractExcerpt(item, "Keyword")
        assertTrue(excerpt.contains("Keyword"))
    }

    @Test
    fun `extractExcerpt returns placeholder when all fields blank`() {
        val item =
            ConfluenceClient.ContentItem(
                body = ConfluenceClient.BodyMap(),
                excerpt = null,
            )
        assertEquals("(No excerpt available)", client().extractExcerpt(item, "Keyword"))
    }

    @Test
    fun `extractExcerpt centers around keyword occurrence`() {
        val longText = "a".repeat(200) + " Keyword " + "b".repeat(200)
        val item = ConfluenceClient.ContentItem(excerpt = longText)
        val excerpt = client().extractExcerpt(item, "Keyword")
        assertTrue(excerpt.contains("Keyword"))
        assertTrue(excerpt.contains("…") || excerpt.contains("...") || excerpt.length < longText.length)
    }

    @Test
    fun `extractExcerpt returns preview when keyword not in body`() {
        val item = ConfluenceClient.ContentItem(excerpt = "Just a short body without the search term")
        val excerpt = client().extractExcerpt(item, "Keyword")
        assertTrue(excerpt.contains("Just a short body"))
    }

    @Test
    fun `extractExcerpt truncates long previews to 300 chars`() {
        val item = ConfluenceClient.ContentItem(excerpt = "x".repeat(500))
        val excerpt = client().extractExcerpt(item, "NonExistent")
        assertTrue(excerpt.length <= 304)
    }

    @Test
    fun `extractExcerpt does not center when keyword is at the start`() {
        val item = ConfluenceClient.ContentItem(excerpt = "Keyword is at the start of this body " + "x".repeat(400))
        val excerpt = client().extractExcerpt(item, "Keyword")
        assertFalse(excerpt.startsWith("…"))
    }

    @Test
    fun `searchKeyword returns empty when config is inactive`() {
        val config = cfg().apply { active = false }
        val result = client().searchKeyword(config, "kw", emptyList(), 1L, 0)
        assertTrue(result.isEmpty())
    }
}
