package com.stacklens.adapter.out.confluence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConfluenceClientModelsCoverageTest {
    @Test
    fun `ContentSearchResponse defaults are applied`() {
        val r = ConfluenceClient.ContentSearchResponse()
        assertTrue(r.results.isEmpty())
        assertEquals(0, r.start)
        assertEquals(25, r.limit)
        assertEquals(0, r.size)
    }

    @Test
    fun `ContentSearchResponse stores provided values verbatim`() {
        val item = ConfluenceClient.ContentItem(id = "1")
        val r = ConfluenceClient.ContentSearchResponse(results = listOf(item), start = 10, limit = 50, size = 11)
        assertEquals(listOf(item), r.results)
        assertEquals(10, r.start)
        assertEquals(50, r.limit)
        assertEquals(11, r.size)
    }

    @Test
    fun `ContentItem defaults are sensible`() {
        val i = ConfluenceClient.ContentItem()
        assertEquals("", i.id)
        assertEquals("page", i.type)
        assertEquals("", i.title)
        assertNull(i.space)
        assertNull(i.links)
        assertNull(i.body)
        assertNull(i.excerpt)
        assertNull(i.children)
    }

    @Test
    fun `ContentItem stores provided values verbatim`() {
        val space = ConfluenceClient.SpaceRef(key = "DEV", name = "Dev")
        val link = ConfluenceClient.LinkMap(webui = "/x")
        val body = ConfluenceClient.BodyMap()
        val child = ConfluenceClient.ChildrenMap()
        val i =
            ConfluenceClient.ContentItem(
                id = "id",
                type = "blogpost",
                title = "T",
                space = space,
                links = link,
                body = body,
                excerpt = "ex",
                children = child,
            )
        assertEquals("id", i.id)
        assertEquals("blogpost", i.type)
        assertEquals("T", i.title)
        assertEquals(space, i.space)
        assertEquals(link, i.links)
        assertEquals(body, i.body)
        assertEquals("ex", i.excerpt)
        assertEquals(child, i.children)
    }

    @Test
    fun `SpaceRef defaults are applied`() {
        val s = ConfluenceClient.SpaceRef()
        assertEquals("", s.key)
        assertEquals("", s.name)
    }

    @Test
    fun `SpaceRef stores provided values verbatim`() {
        val s = ConfluenceClient.SpaceRef(key = "K", name = "N")
        assertEquals("K", s.key)
        assertEquals("N", s.name)
    }

    @Test
    fun `LinkMap all fields default to null`() {
        val l = ConfluenceClient.LinkMap()
        assertNull(l.webui)
        assertNull(l.base)
        assertNull(l.download)
    }

    @Test
    fun `LinkMap stores provided values verbatim`() {
        val l = ConfluenceClient.LinkMap(webui = "/w", base = "/b", download = "/d")
        assertEquals("/w", l.webui)
        assertEquals("/b", l.base)
        assertEquals("/d", l.download)
    }

    @Test
    fun `BodyMap defaults are applied`() {
        val b = ConfluenceClient.BodyMap()
        assertNull(b.view)
        assertNull(b.storage)
    }

    @Test
    fun `BodyMap stores provided values verbatim`() {
        val v = ConfluenceClient.BodyContent(value = "v")
        val s = ConfluenceClient.BodyContent(value = "s")
        val b = ConfluenceClient.BodyMap(view = v, storage = s)
        assertEquals(v, b.view)
        assertEquals(s, b.storage)
    }

    @Test
    fun `ChildrenMap defaults to null attachment`() {
        val c = ConfluenceClient.ChildrenMap()
        assertNull(c.attachment)
    }

    @Test
    fun `ChildrenMap stores provided attachment verbatim`() {
        val a = ConfluenceClient.AttachmentContainer()
        val c = ConfluenceClient.ChildrenMap(attachment = a)
        assertEquals(a, c.attachment)
    }

    @Test
    fun `AttachmentContainer defaults are applied`() {
        val a = ConfluenceClient.AttachmentContainer()
        assertTrue(a.results.isEmpty())
    }

    @Test
    fun `AttachmentContainer stores provided results verbatim`() {
        val item = ConfluenceClient.AttachmentItem(id = "1")
        val a = ConfluenceClient.AttachmentContainer(results = listOf(item))
        assertEquals(listOf(item), a.results)
    }

    @Test
    fun `AttachmentItem defaults are sensible`() {
        val a = ConfluenceClient.AttachmentItem()
        assertEquals("", a.id)
        assertEquals("", a.title)
        assertNull(a.links)
        assertNull(a.metadata)
    }

    @Test
    fun `AttachmentItem stores provided values verbatim`() {
        val link = ConfluenceClient.LinkMap(webui = "/w")
        val meta = ConfluenceClient.AttachmentMeta(mediaType = "image/png")
        val a = ConfluenceClient.AttachmentItem(id = "i", title = "t", links = link, metadata = meta)
        assertEquals("i", a.id)
        assertEquals("t", a.title)
        assertEquals(link, a.links)
        assertEquals(meta, a.metadata)
    }

    @Test
    fun `AttachmentMeta default mediaType is null`() {
        val m = ConfluenceClient.AttachmentMeta()
        assertNull(m.mediaType)
    }

    @Test
    fun `AttachmentMeta stores provided mediaType verbatim`() {
        val m = ConfluenceClient.AttachmentMeta(mediaType = "application/json")
        assertEquals("application/json", m.mediaType)
    }

    @Test
    fun `BodyContent default value is null`() {
        val b = ConfluenceClient.BodyContent()
        assertNull(b.value)
    }

    @Test
    fun `BodyContent stores provided value verbatim`() {
        val b = ConfluenceClient.BodyContent(value = "<p>hi</p>")
        assertEquals("<p>hi</p>", b.value)
        assertNotNull(b.value)
    }
}
