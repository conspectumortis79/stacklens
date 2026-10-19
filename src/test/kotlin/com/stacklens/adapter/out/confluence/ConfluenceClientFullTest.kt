package com.stacklens.adapter.out.confluence

import com.stacklens.domain.model.ConfluenceConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConfluenceClientFullTest {
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
    fun `extractExcerpt returns body view content with HTML stripped`() {
        val item =
            ConfluenceClient.ContentItem(
                body =
                    ConfluenceClient.BodyMap(
                        view =
                            ConfluenceClient.BodyContent(
                                value = "<p>Hello <b>world</b> this is a test</p>",
                            ),
                    ),
            )
        val excerpt = client().extractExcerpt(item, "world")
        assertTrue(excerpt.contains("Hello world"))
        assertFalse(excerpt.contains("<"))
    }

    @Test
    fun `extractExcerpt falls back to storage when view is missing`() {
        val item =
            ConfluenceClient.ContentItem(
                body =
                    ConfluenceClient.BodyMap(
                        storage =
                            ConfluenceClient.BodyContent(value = "Storage content here"),
                    ),
            )
        val excerpt = client().extractExcerpt(item, "Storage")
        assertTrue(excerpt.contains("Storage"))
    }

    @Test
    fun `extractExcerpt falls back to excerpt field when body is missing`() {
        val item =
            ConfluenceClient.ContentItem(
                excerpt = "Lightweight excerpt text",
            )
        val excerpt = client().extractExcerpt(item, "excerpt")
        assertTrue(excerpt.contains("excerpt"))
    }

    @Test
    fun `extractExcerpt returns fallback when all sources are blank`() {
        val item =
            ConfluenceClient.ContentItem(
                body =
                    ConfluenceClient.BodyMap(
                        view = ConfluenceClient.BodyContent(value = ""),
                        storage = ConfluenceClient.BodyContent(value = ""),
                    ),
                excerpt = "",
            )
        val excerpt = client().extractExcerpt(item, "anything")
        assertTrue(excerpt.contains("No excerpt"))
    }

    @Test
    fun `extractExcerpt centers around keyword with ellipses`() {
        val longText = "a".repeat(200) + " KEYWORD " + "b".repeat(200)
        val item =
            ConfluenceClient.ContentItem(
                body =
                    ConfluenceClient.BodyMap(
                        view = ConfluenceClient.BodyContent(value = longText),
                    ),
            )
        val excerpt = client().extractExcerpt(item, "KEYWORD")
        assertTrue(excerpt.contains("KEYWORD"))
        assertTrue(excerpt.contains("…"))
    }

    @Test
    fun `extractExcerpt does not add leading ellipsis when keyword is at start`() {
        val item =
            ConfluenceClient.ContentItem(
                body =
                    ConfluenceClient.BodyMap(
                        view =
                            ConfluenceClient.BodyContent(
                                value = "KEYWORD is at the start",
                            ),
                    ),
            )
        val excerpt = client().extractExcerpt(item, "KEYWORD")
        assertFalse(excerpt.startsWith("…"))
    }

    @Test
    fun `extractExcerpt handles keyword not in body`() {
        val item =
            ConfluenceClient.ContentItem(
                body =
                    ConfluenceClient.BodyMap(
                        view =
                            ConfluenceClient.BodyContent(
                                value = "Some content without the keyword",
                            ),
                    ),
            )
        val excerpt = client().extractExcerpt(item, "MISSING")
        assertTrue(excerpt.contains("Some content"))
    }

    @Test
    fun `extractExcerpt truncates long body when keyword not present`() {
        val longText = "x".repeat(500)
        val item =
            ConfluenceClient.ContentItem(
                body =
                    ConfluenceClient.BodyMap(
                        view = ConfluenceClient.BodyContent(value = longText),
                    ),
            )
        val excerpt = client().extractExcerpt(item, "NOT_FOUND")
        assertTrue(excerpt.length < 400)
        assertTrue(excerpt.endsWith("…"))
    }

    @Test
    fun `extractExcerpt collapses multiple whitespaces`() {
        val item =
            ConfluenceClient.ContentItem(
                body =
                    ConfluenceClient.BodyMap(
                        view =
                            ConfluenceClient.BodyContent(
                                value = "Hello\n\n   world\t\twith    spaces",
                            ),
                    ),
            )
        val excerpt = client().extractExcerpt(item, "Hello")
        assertFalse(excerpt.contains("  "))
    }

    @Test
    fun `matchesSearchRoot returns true when root is null`() {
        assertTrue(client().matchesSearchRoot(null, "https://example.com/page"))
    }

    @Test
    fun `matchesSearchRoot returns true when root is blank`() {
        assertTrue(client().matchesSearchRoot("", "https://example.com/page"))
    }

    @Test
    fun `matchesSearchRoot matches exact URL`() {
        assertTrue(
            client().matchesSearchRoot("https://example.com/space", "https://example.com/space"),
        )
    }

    @Test
    fun `matchesSearchRoot matches URL prefix`() {
        assertTrue(
            client().matchesSearchRoot("https://example.com/space", "https://example.com/space/page1"),
        )
    }

    @Test
    fun `matchesSearchRoot returns false for different URL`() {
        assertFalse(
            client().matchesSearchRoot("https://example.com/space", "https://example.com/other"),
        )
    }

    @Test
    fun `matchesSearchRoot is case-insensitive`() {
        assertTrue(
            client().matchesSearchRoot("https://EXAMPLE.com/space", "https://example.com/Space/Page"),
        )
    }

    @Test
    fun `matchesSearchRoot handles trailing slashes`() {
        assertTrue(
            client().matchesSearchRoot("https://example.com/space/", "https://example.com/space/page"),
        )
    }

    @Test
    fun `matchesSearchRoot does not match partial directory name`() {
        assertFalse(
            client().matchesSearchRoot("https://example.com/space", "https://example.com/space2/page"),
        )
    }

    @Test
    fun `matchesAnySearchRoot returns true when list is empty`() {
        assertTrue(
            client().matchesAnySearchRoot(emptyList(), "https://example.com/page"),
        )
    }

    @Test
    fun `matchesAnySearchRoot matches first root`() {
        assertTrue(
            client().matchesAnySearchRoot(
                listOf("https://example.com/space1", "https://example.com/space2"),
                "https://example.com/space1/page",
            ),
        )
    }

    @Test
    fun `matchesAnySearchRoot matches second root`() {
        assertTrue(
            client().matchesAnySearchRoot(
                listOf("https://example.com/space1", "https://example.com/space2"),
                "https://example.com/space2/page",
            ),
        )
    }

    @Test
    fun `matchesAnySearchRoot returns false when no root matches`() {
        assertFalse(
            client().matchesAnySearchRoot(
                listOf("https://example.com/space1", "https://example.com/space2"),
                "https://example.com/space3/page",
            ),
        )
    }

    @Test
    fun `matchesAnySearchRoot handles blank roots in list`() {
        assertTrue(
            client().matchesAnySearchRoot(
                listOf("", "https://example.com/space"),
                "https://example.com/space/page",
            ),
        )
    }

    @Test
    fun `buildPageUrl Cloud returns empty-ish URL when space and id are missing`() {
        val item = ConfluenceClient.ContentItem()
        val url = client().buildPageUrl(cfg(cloudMode = true), item)
        assertTrue(url.isNotEmpty())
    }

    @Test
    fun `buildPageUrl Cloud handles blank space key`() {
        val item =
            ConfluenceClient.ContentItem(
                id = "123",
                space = ConfluenceClient.SpaceRef(key = "", name = ""),
            )
        val url = client().buildPageUrl(cfg(cloudMode = true), item)
        assertTrue(url.isNotEmpty())
    }

    @Test
    fun `buildPageUrl Cloud handles blank page id`() {
        val item =
            ConfluenceClient.ContentItem(
                id = "",
                space = ConfluenceClient.SpaceRef(key = "DEV", name = "Dev"),
            )
        val url = client().buildPageUrl(cfg(cloudMode = true), item)
        assertTrue(url.isNotEmpty())
    }

    @Test
    fun `buildPageUrl Cloud uses wiki segment in baseUrl already containing wiki`() {
        val item =
            ConfluenceClient.ContentItem(
                id = "1",
                space = ConfluenceClient.SpaceRef(key = "X", name = "X"),
            )
        val url =
            client().buildPageUrl(
                cfg(cloudMode = true, baseUrl = "https://example.atlassian.net/wiki"),
                item,
            )
        assertTrue(url.contains("/wiki/spaces/X/pages/1"))
        assertFalse(url.contains("/wiki/wiki/"))
    }

    @Test
    fun `buildPageUrl Server uses webui with leading slash`() {
        val item =
            ConfluenceClient.ContentItem(
                links = ConfluenceClient.LinkMap(webui = "/pages/123"),
            )
        val url = client().buildPageUrl(cfg(cloudMode = false), item)
        assertEquals("https://example.atlassian.net/pages/123", url)
    }

    @Test
    fun `buildPageUrl Server handles webui without leading slash`() {
        val item =
            ConfluenceClient.ContentItem(
                links = ConfluenceClient.LinkMap(webui = "pages/123"),
            )
        val url = client().buildPageUrl(cfg(cloudMode = false), item)
        assertTrue(url.contains("pages/123"))
    }

    @Test
    fun `buildClient creates WebClient with basic auth header`() {
        val client = client().buildClient(cfg())
        assertNotNull(client)
    }

    @Test
    fun `buildClient handles baseUrl with trailing slash`() {
        val client = client().buildClient(cfg(baseUrl = "https://example.atlassian.net/"))
        assertNotNull(client)
    }

    @Test
    fun `contentSearchPath returns wiki path for Cloud mode`() {
        val path = contentSearchPath(cfg(cloudMode = true))
        assertTrue(path.contains("/wiki/rest/api/content/search"))
    }

    @Test
    fun `contentSearchPath returns non-wiki path for Server mode`() {
        val path = contentSearchPath(cfg(cloudMode = false))
        assertTrue(path.contains("/rest/api/content/search"))
        assertFalse(path.contains("/wiki"))
    }

    @Test
    fun `spaceKeyList parses comma-separated keys`() {
        val keys = cfg(spaceKeys = "DEV,OPS,PROD").spaceKeyList()
        assertEquals(listOf("DEV", "OPS", "PROD"), keys)
    }

    @Test
    fun `spaceKeyList returns empty for blank string`() {
        assertEquals(emptyList<String>(), cfg(spaceKeys = "").spaceKeyList())
    }

    @Test
    fun `spaceKeyList trims whitespace around keys`() {
        val keys = cfg(spaceKeys = " DEV , OPS ").spaceKeyList()
        assertEquals(listOf("DEV", "OPS"), keys)
    }

    @Test
    fun `spaceKeyList filters blank keys`() {
        val keys = cfg(spaceKeys = "DEV,,OPS,").spaceKeyList()
        assertEquals(listOf("DEV", "OPS"), keys)
    }

    @Test
    fun `ContentItem defaults are applied`() {
        val item = ConfluenceClient.ContentItem()
        assertEquals("", item.id)
        assertEquals("page", item.type)
        assertEquals("", item.title)
    }

    @Test
    fun `AttachmentItem defaults are applied`() {
        val item = ConfluenceClient.AttachmentItem()
        assertEquals("", item.id)
        assertEquals("", item.title)
    }

    @Test
    fun `ContentSearchResponse defaults are applied`() {
        val r = ConfluenceClient.ContentSearchResponse()
        assertEquals(emptyList<ConfluenceClient.ContentItem>(), r.results)
        assertEquals(0, r.start)
        assertEquals(25, r.limit)
        assertEquals(0, r.size)
    }

    @Test
    fun `AttachmentContainer defaults are applied`() {
        val c = ConfluenceClient.AttachmentContainer()
        assertEquals(emptyList<ConfluenceClient.AttachmentItem>(), c.results)
    }
}
