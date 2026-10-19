package com.stacklens.adapter.out.gitlab

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class ContextMapAdapterDtoBranchTest {
    private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

    @Test
    fun `ContextProject deserializes all fields`() {
        val json = """{"id":1,"name":"proj","path_with_namespace":"g/p","default_branch":"main"}"""
        val p = mapper.readValue(json, ContextMapAdapter.ContextProject::class.java)
        assertEquals(1L, p.id)
        assertEquals("proj", p.name)
        assertEquals("g/p", p.pathWithNamespace)
        assertEquals("main", p.defaultBranch)
    }

    @Test
    fun `ContextProject default branch is main when null`() {
        val p = ContextMapAdapter.ContextProject()
        assertEquals("main", p.defaultBranch)
    }

    @Test
    fun `ContextProject equality holds by field value`() {
        val a = ContextMapAdapter.ContextProject(id = 1, name = "p", pathWithNamespace = "g/p", defaultBranch = "main")
        val b = ContextMapAdapter.ContextProject(id = 1, name = "p", pathWithNamespace = "g/p", defaultBranch = "main")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `ContextProject per-field equality branches`() {
        val base = ContextMapAdapter.ContextProject(id = 1, name = "p", pathWithNamespace = "g/p", defaultBranch = "main")
        assertNotEquals(base, base.copy(id = 99))
        assertNotEquals(base, base.copy(name = "x"))
        assertNotEquals(base, base.copy(pathWithNamespace = "g/q"))
        assertNotEquals(base, base.copy(defaultBranch = "develop"))
    }

    @Test
    fun `BlobResult deserializes all fields`() {
        val json = """{"project_id":1,"path":"src/Foo.kt","ref":"main","startline":10,"data":"code"}"""
        val r = mapper.readValue(json, ContextMapAdapter.BlobResult::class.java)
        assertEquals(1L, r.projectId)
        assertEquals("src/Foo.kt", r.path)
        assertEquals("main", r.ref)
        assertEquals(10, r.startline)
        assertEquals("code", r.data)
    }

    @Test
    fun `BlobResult equality holds by field value`() {
        val a = ContextMapAdapter.BlobResult(projectId = 1, path = "p", ref = "main", startline = 10, data = "d")
        val b = ContextMapAdapter.BlobResult(projectId = 1, path = "p", ref = "main", startline = 10, data = "d")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `BlobResult per-field equality branches`() {
        val base = ContextMapAdapter.BlobResult(projectId = 1, path = "p", ref = "main", startline = 10, data = "d")
        assertNotEquals(base, base.copy(projectId = 99))
        assertNotEquals(base, base.copy(path = "q"))
        assertNotEquals(base, base.copy(ref = "dev"))
        assertNotEquals(base, base.copy(startline = 20))
        assertNotEquals(base, base.copy(data = "x"))
    }

    @Test
    fun `TreeEntry deserializes all fields`() {
        val json = """{"id":"a1","name":"Foo.kt","path":"src/Foo.kt","type":"blob"}"""
        val t = mapper.readValue(json, ContextMapAdapter.TreeEntry::class.java)
        assertEquals("a1", t.id)
        assertEquals("Foo.kt", t.name)
        assertEquals("src/Foo.kt", t.path)
        assertEquals("blob", t.type)
    }

    @Test
    fun `TreeEntry equality holds by field value`() {
        val a = ContextMapAdapter.TreeEntry(id = "1", name = "F", path = "p", type = "blob")
        val b = ContextMapAdapter.TreeEntry(id = "1", name = "F", path = "p", type = "blob")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `TreeEntry per-field equality branches`() {
        val base = ContextMapAdapter.TreeEntry(id = "1", name = "F", path = "p", type = "blob")
        assertNotEquals(base, base.copy(id = "2"))
        assertNotEquals(base, base.copy(name = "X"))
        assertNotEquals(base, base.copy(path = "q"))
        assertNotEquals(base, base.copy(type = "tree"))
    }
}
