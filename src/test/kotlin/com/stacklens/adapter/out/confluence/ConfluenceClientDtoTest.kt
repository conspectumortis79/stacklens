package com.stacklens.adapter.out.confluence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ConfluenceClientDtoTest {
    @Test
    fun `ContentSearchResponse default and explicit`() {
        val r = ConfluenceClient.ContentSearchResponse()
        assertEquals(emptyList<ConfluenceClient.ContentItem>(), r.results)
        assertEquals(0, r.start)
        assertEquals(25, r.limit)
        assertEquals(0, r.size)
        val r2 =
            ConfluenceClient.ContentSearchResponse(
                results = listOf(ConfluenceClient.ContentItem()),
                start = 1,
                limit = 50,
                size = 10,
            )
        assertEquals(1, r2.results.size)
        assertEquals(50, r2.limit)
    }

    @Test
    fun `ContentItem default and explicit`() {
        val r = ConfluenceClient.ContentItem()
        assertEquals("", r.id)
        assertEquals("page", r.type)
        assertEquals("", r.title)
        assertNull(r.space)
        assertNull(r.links)
        assertNull(r.body)
        assertNull(r.excerpt)
        assertNull(r.children)
        val r2 =
            ConfluenceClient.ContentItem(
                id = "i",
                type = "blogpost",
                title = "T",
                space = ConfluenceClient.SpaceRef(key = "k", name = "n"),
                links = ConfluenceClient.LinkMap(webui = "/w"),
                body = ConfluenceClient.BodyMap(),
                excerpt = "ex",
                children = ConfluenceClient.ChildrenMap(),
            )
        assertEquals("i", r2.id)
        assertEquals("blogpost", r2.type)
        assertEquals("T", r2.title)
        assertEquals("k", r2.space?.key)
        assertEquals("/w", r2.links?.webui)
        assertNotNull(r2.body)
        assertEquals("ex", r2.excerpt)
    }

    @Test
    fun `SpaceRef default and explicit`() {
        assertEquals("", ConfluenceClient.SpaceRef().key)
        assertEquals("", ConfluenceClient.SpaceRef().name)
        val r = ConfluenceClient.SpaceRef(key = "k", name = "n")
        assertEquals("k", r.key)
        assertEquals("n", r.name)
    }

    @Test
    fun `LinkMap default and explicit`() {
        assertNull(ConfluenceClient.LinkMap().webui)
        val r = ConfluenceClient.LinkMap(webui = "/w", base = "/b", download = "/d")
        assertEquals("/w", r.webui)
        assertEquals("/b", r.base)
        assertEquals("/d", r.download)
    }

    @Test
    fun `BodyMap default and explicit`() {
        assertNull(ConfluenceClient.BodyMap().view)
        assertNull(ConfluenceClient.BodyMap().storage)
        val r =
            ConfluenceClient.BodyMap(
                view = ConfluenceClient.BodyContent(),
                storage = ConfluenceClient.BodyContent(),
            )
        assertNotNull(r.view)
        assertNotNull(r.storage)
    }

    @Test
    fun `ChildrenMap default and explicit`() {
        assertNull(ConfluenceClient.ChildrenMap().attachment)
        val r = ConfluenceClient.ChildrenMap(attachment = ConfluenceClient.AttachmentContainer())
        assertNotNull(r.attachment)
    }

    @Test
    fun `AttachmentContainer default and explicit`() {
        assertEquals(emptyList<ConfluenceClient.AttachmentItem>(), ConfluenceClient.AttachmentContainer().results)
        val r =
            ConfluenceClient.AttachmentContainer(results = listOf(ConfluenceClient.AttachmentItem()))
        assertEquals(1, r.results.size)
    }

    @Test
    fun `AttachmentItem default and explicit`() {
        assertEquals("", ConfluenceClient.AttachmentItem().id)
        assertEquals("", ConfluenceClient.AttachmentItem().title)
        assertNull(ConfluenceClient.AttachmentItem().links)
        assertNull(ConfluenceClient.AttachmentItem().metadata)
        val r =
            ConfluenceClient.AttachmentItem(
                id = "1",
                title = "f",
                links = ConfluenceClient.LinkMap(),
                metadata = ConfluenceClient.AttachmentMeta(),
            )
        assertEquals("1", r.id)
    }
}
