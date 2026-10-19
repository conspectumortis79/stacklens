package com.stacklens.adapter.out.shared

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MrFetchAdapterDtoBranchTest {
    private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

    @Test
    fun `GlChangesResponse deserializes changes and diffRefs`() {
        val json = """{"changes":[],"diff_refs":{"base_sha":"abc","start_sha":"def","head_sha":"ghi"}}"""
        val r = mapper.readValue(json, GitlabMrFetchAdapter.GlChangesResponse::class.java)
        assertEquals(0, r.changes.size)
        assertEquals("abc", r.diffRefs?.baseSha)
        assertEquals("def", r.diffRefs?.startSha)
        assertEquals("ghi", r.diffRefs?.headSha)
    }

    @Test
    fun `GlChangesResponse equality holds by field value`() {
        val a = GitlabMrFetchAdapter.GlChangesResponse()
        val b = GitlabMrFetchAdapter.GlChangesResponse()
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `GlAuthor with name deserializes correctly`() {
        val json = """{"name":"Alice"}"""
        val r = mapper.readValue(json, GitlabMrFetchAdapter.GlAuthor::class.java)
        assertEquals("Alice", r.name)
    }

    @Test
    fun `GlDiffRefs with base_start_head deserializes correctly`() {
        val json = """{"base_sha":"old","start_sha":"mid","head_sha":"new"}"""
        val r = mapper.readValue(json, GitlabMrFetchAdapter.GlDiffRefs::class.java)
        assertEquals("old", r.baseSha)
        assertEquals("mid", r.startSha)
        assertEquals("new", r.headSha)
    }

    @Test
    fun `GlDiffFile with newPath deserializes correctly`() {
        val json = """{"old_path":"src/Bar.kt","new_path":"src/Foo.kt","new_file":true,"diff":"@@ -1 +1 @@","deleted":false}"""
        val r = mapper.readValue(json, GitlabMrFetchAdapter.GlDiffFile::class.java)
        assertEquals("src/Foo.kt", r.newPath)
        assertEquals("src/Bar.kt", r.oldPath)
        assertEquals(true, r.newFile)
        assertEquals(false, r.deleted)
    }

    @Test
    fun `GhRef with ref and sha deserializes correctly`() {
        val json = """{"ref":"refs/heads/main","sha":"abc123"}"""
        val r = mapper.readValue(json, GithubPrFetchAdapter.GhRef::class.java)
        assertEquals("refs/heads/main", r.ref)
        assertEquals("abc123", r.sha)
    }

    @Test
    fun `GhUser with login deserializes correctly`() {
        val json = """{"login":"alice"}"""
        val r = mapper.readValue(json, GithubPrFetchAdapter.GhUser::class.java)
        assertEquals("alice", r.login)
    }

    @Test
    fun `GhFile with filename deserializes correctly`() {
        val json = """{"filename":"src/Main.kt","status":"modified"}"""
        val r = mapper.readValue(json, GithubPrFetchAdapter.GhFile::class.java)
        assertEquals("src/Main.kt", r.filename)
        assertEquals("modified", r.status)
    }
}
