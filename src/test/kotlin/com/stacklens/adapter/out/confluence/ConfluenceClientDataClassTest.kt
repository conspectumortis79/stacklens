package com.stacklens.adapter.out.confluence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class ConfluenceClientDataClassTest {
    @Test
    fun `BodyContent equals and hashCode`() {
        val a = ConfluenceClient.BodyContent(value = "x")
        val b = ConfluenceClient.BodyContent(value = "x")
        val c = ConfluenceClient.BodyContent(value = "y")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
        assertEquals("x", a.value)
        assertEquals("BodyContent(value=x)", a.toString())
        val copy = a.copy()
        assertEquals(a, copy)
    }

    @Test
    fun `SpaceRef equals and hashCode`() {
        val a = ConfluenceClient.SpaceRef(key = "k", name = "n")
        val b = ConfluenceClient.SpaceRef(key = "k", name = "n")
        val c = ConfluenceClient.SpaceRef(key = "x", name = "n")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
        assertEquals("SpaceRef(key=k, name=n)", a.toString())
        val copy = a.copy()
        assertEquals(a, copy)
    }

    @Test
    fun `AttachmentMeta equals and hashCode`() {
        val a = ConfluenceClient.AttachmentMeta(mediaType = "image/png")
        val b = ConfluenceClient.AttachmentMeta(mediaType = "image/png")
        val c = ConfluenceClient.AttachmentMeta(mediaType = "text/plain")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
        assertEquals("AttachmentMeta(mediaType=image/png)", a.toString())
        val copy = a.copy()
        assertEquals(a, copy)
    }

    @Test
    fun `ChildrenMap equals and hashCode`() {
        val a = ConfluenceClient.ChildrenMap()
        val b = ConfluenceClient.ChildrenMap()
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertEquals("ChildrenMap(attachment=null)", a.toString())
        val copy = a.copy()
        assertEquals(a, copy)
    }
}
