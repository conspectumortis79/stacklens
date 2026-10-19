package com.stacklens.adapter.out.confluence

import com.stacklens.domain.model.ConfluenceConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConfluenceClientHelpersCoverageTest {
    private val client =
        ConfluenceClient()

    private fun confluenceConfig(
        baseUrl: String = "https://example.atlassian.net",
        cloudMode: Boolean = true,
        username: String = "u",
        apiToken: String = "t",
    ) = ConfluenceConfig(name = "n", baseUrl = baseUrl, username = username, apiToken = apiToken, cloudMode = cloudMode)

    @Test
    fun `buildPageUrl returns absolute webuiPath as-is`() {
        val cfg = confluenceConfig()
        val item = ConfluenceClient.ContentItem(links = ConfluenceClient.LinkMap(webui = "https://other.example/x"))
        assertEquals("https://other.example/x", client.buildPageUrl(cfg, item))
    }

    @Test
    fun `buildPageUrl builds Cloud canonical URL with wiki prefix`() {
        val cfg = confluenceConfig(baseUrl = "https://example.atlassian.net", cloudMode = true)
        val item =
            ConfluenceClient.ContentItem(
                id = "42",
                space = ConfluenceClient.SpaceRef(key = "DEV"),
            )
        assertEquals(
            "https://example.atlassian.net/wiki/spaces/DEV/pages/42",
            client.buildPageUrl(cfg, item),
        )
    }

    @Test
    fun `buildPageUrl keeps wiki prefix when baseUrl already contains it`() {
        val cfg = confluenceConfig(baseUrl = "https://example.atlassian.net/wiki", cloudMode = true)
        val item =
            ConfluenceClient.ContentItem(
                id = "1",
                space = ConfluenceClient.SpaceRef(key = "K"),
            )
        assertEquals(
            "https://example.atlassian.net/wiki/spaces/K/pages/1",
            client.buildPageUrl(cfg, item),
        )
    }

    @Test
    fun `buildPageUrl falls back to webuiPath for Server DC`() {
        val cfg = confluenceConfig(baseUrl = "https://confluence.example.com", cloudMode = false)
        val item = ConfluenceClient.ContentItem(links = ConfluenceClient.LinkMap(webui = "/spaces/X/pages/9"))
        assertEquals("https://confluence.example.com/spaces/X/pages/9", client.buildPageUrl(cfg, item))
    }

    @Test
    fun `buildPageUrl returns empty Cloud URL when space and id are missing`() {
        val cfg = confluenceConfig(cloudMode = true)
        val item = ConfluenceClient.ContentItem()
        assertEquals("${cfg.baseUrl.trimEnd('/')}", client.buildPageUrl(cfg, item))
    }

    @Test
    fun `matchesSearchRoot returns true for null and blank root`() {
        assertTrue(client.matchesSearchRoot(null, "https://x/page"))
        assertTrue(client.matchesSearchRoot("", "https://x/page"))
        assertTrue(client.matchesSearchRoot("   ", "https://x/page"))
    }

    @Test
    fun `matchesSearchRoot returns true for exact match`() {
        assertTrue(client.matchesSearchRoot("https://x/page", "https://x/page"))
    }

    @Test
    fun `matchesSearchRoot returns true for prefix match`() {
        assertTrue(client.matchesSearchRoot("https://x/root", "https://x/root/child"))
    }

    @Test
    fun `matchesSearchRoot returns false for unrelated URL`() {
        assertFalse(client.matchesSearchRoot("https://x/root", "https://x/other"))
    }

    @Test
    fun `matchesSearchRoot is case-insensitive and ignores trailing slashes`() {
        assertTrue(client.matchesSearchRoot("https://X/Root/", "https://x/root/child"))
        assertTrue(client.matchesSearchRoot("https://X/Root/", "https://x/root/"))
    }

    @Test
    fun `matchesAnySearchRoot returns true when list is empty`() {
        assertTrue(client.matchesAnySearchRoot(emptyList(), "https://x/page"))
    }

    @Test
    fun `matchesAnySearchRoot returns true when at least one root matches`() {
        assertTrue(
            client.matchesAnySearchRoot(
                listOf("https://x/other", "https://x/page"),
                "https://x/page/child",
            ),
        )
    }

    @Test
    fun `matchesAnySearchRoot returns false when no root matches`() {
        assertFalse(
            client.matchesAnySearchRoot(
                listOf("https://x/one", "https://x/two"),
                "https://x/three",
            ),
        )
    }

    @Test
    fun `extractExcerpt returns placeholder when no body and no excerpt`() {
        val item = ConfluenceClient.ContentItem()
        assertEquals("(No excerpt available)", client.extractExcerpt(item, "kw"))
    }

    @Test
    fun `extractExcerpt prefers body view HTML and strips tags`() {
        val item =
            ConfluenceClient.ContentItem(
                body = ConfluenceClient.BodyMap(view = ConfluenceClient.BodyContent(value = "<p>hello <b>kw</b> world</p>")),
            )
        val ex = client.extractExcerpt(item, "kw")
        assertTrue("kw" in ex)
        assertFalse("<" in ex)
        assertFalse(">" in ex)
    }

    @Test
    fun `extractExcerpt falls back to body storage when view missing`() {
        val item =
            ConfluenceClient.ContentItem(
                body = ConfluenceClient.BodyMap(storage = ConfluenceClient.BodyContent(value = "<p>kw storage</p>")),
            )
        assertEquals("kw storage", client.extractExcerpt(item, "kw"))
    }

    @Test
    fun `extractExcerpt falls back to excerpt field when body is null`() {
        val item = ConfluenceClient.ContentItem(excerpt = "kw excerpt")
        assertEquals("kw excerpt", client.extractExcerpt(item, "kw"))
    }

    @Test
    fun `extractExcerpt adds ellipsis when excerpt is truncated`() {
        val item =
            ConfluenceClient.ContentItem(
                body = ConfluenceClient.BodyMap(view = ConfluenceClient.BodyContent(value = "a".repeat(500))),
            )
        val ex = client.extractExcerpt(item, "zzz")
        assertTrue(ex.endsWith("…"))
    }

    @Test
    fun `extractExcerpt centres excerpt around first keyword hit`() {
        val long = "x".repeat(400) + "kw" + "y".repeat(400)
        val item =
            ConfluenceClient.ContentItem(
                body = ConfluenceClient.BodyMap(view = ConfluenceClient.BodyContent(value = long)),
            )
        val ex = client.extractExcerpt(item, "kw")
        assertTrue(ex.contains("kw"))
        assertTrue(ex.startsWith("…"))
    }

    @Test
    fun `extractExcerpt handles blank plain content after stripping`() {
        val item =
            ConfluenceClient.ContentItem(
                body = ConfluenceClient.BodyMap(view = ConfluenceClient.BodyContent(value = "   <p></p>")),
            )
        assertEquals("(No excerpt available)", client.extractExcerpt(item, "kw"))
    }
}
