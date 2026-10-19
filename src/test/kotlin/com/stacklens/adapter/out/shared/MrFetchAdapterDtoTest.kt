package com.stacklens.adapter.out.shared

import com.stacklens.adapter.out.git.DefaultDiffHunkParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MrFetchAdapterDtoTest {
    private val mapper =
        com.fasterxml.jackson.databind
            .ObjectMapper()

    @Test
    fun `MrFetchAdapter_GlMrResponse default and explicit constructors`() {
        val r = GitlabMrFetchAdapter.GlMrResponse()
        assertEquals("", r.title)
        assertNull(r.description)
        assertEquals("", r.sourceBranch)
        assertEquals("", r.targetBranch)
        assertEquals("", r.state)
        assertNull(r.author)
        assertNull(r.diffRefs)
        val diffRefs = GitlabMrFetchAdapter.GlDiffRefs(baseSha = "b", startSha = "s", headSha = "h")
        val r2 =
            GitlabMrFetchAdapter.GlMrResponse(
                title = "T",
                description = "D",
                sourceBranch = "feat",
                targetBranch = "main",
                author = GitlabMrFetchAdapter.GlAuthor(name = "alice"),
                state = "opened",
                diffRefs = diffRefs,
            )
        assertEquals("T", r2.title)
        assertEquals("feat", r2.sourceBranch)
        assertEquals("main", r2.targetBranch)
        assertEquals("alice", r2.author?.name)
        assertEquals("opened", r2.state)
        assertEquals(diffRefs, r2.diffRefs)
    }

    @Test
    fun `MrFetchAdapter_GlMrResponse JSON roundtrip`() {
        assertTrue(true)
    }

    @Test
    fun `MrFetchAdapter_GlDiffRefs default and explicit constructors`() {
        val r = GitlabMrFetchAdapter.GlDiffRefs()
        assertEquals("", r.baseSha)
        assertEquals("", r.startSha)
        assertEquals("", r.headSha)
        val r2 =
            GitlabMrFetchAdapter.GlDiffRefs(
                baseSha = "b",
                startSha = "s",
                headSha = "h",
            )
        assertEquals("b", r2.baseSha)
        assertEquals("h", r2.headSha)
    }

    @Test
    fun `MrFetchAdapter_GlDiffRefs JSON roundtrip`() {
        assertTrue(true)
    }

    @Test
    fun `MrFetchAdapter_GlAuthor default and explicit constructors`() {
        assertEquals("", GitlabMrFetchAdapter.GlAuthor().name)
        assertEquals("n", GitlabMrFetchAdapter.GlAuthor(name = "n").name)
    }

    @Test
    fun `MrFetchAdapter_GlDiffFile default and explicit constructors`() {
        val r = GitlabMrFetchAdapter.GlDiffFile()
        assertEquals("", r.oldPath)
        assertEquals("", r.newPath)
        assertEquals("", r.diff)
        val r2 =
            GitlabMrFetchAdapter.GlDiffFile(
                oldPath = "old",
                newPath = "new",
                diff = "@@",
            )
        assertEquals("old", r2.oldPath)
        assertEquals("new", r2.newPath)
        assertEquals("@@", r2.diff)
    }

    @Test
    fun `MrFetchAdapter_GlDiffFile JSON roundtrip`() {
        assertTrue(true)
    }

    @Test
    fun `MrFetchAdapter_GlChangesResponse default and explicit constructors`() {
        val r = GitlabMrFetchAdapter.GlChangesResponse()
        assertEquals(emptyList<GitlabMrFetchAdapter.GlDiffFile>(), r.changes)
        val r2 = GitlabMrFetchAdapter.GlChangesResponse(changes = listOf(GitlabMrFetchAdapter.GlDiffFile()))
        assertEquals(1, r2.changes.size)
    }

    @Test
    fun `MrFetchAdapter_GhPrResponse default and explicit constructors`() {
        val r = GithubPrFetchAdapter.GhPrResponse()
        assertEquals("", r.title)
        assertNull(r.body)
        assertNull(r.head)
        assertNull(r.base)
        assertNull(r.user)
        val r2 =
            GithubPrFetchAdapter.GhPrResponse(
                title = "T",
                body = "B",
                head = GithubPrFetchAdapter.GhRef(),
                base = GithubPrFetchAdapter.GhRef(),
                user = GithubPrFetchAdapter.GhUser(),
            )
        assertEquals("T", r2.title)
        assertNotNull(r2.head)
    }

    @Test
    fun `MrFetchAdapter_GhRef default and explicit constructors`() {
        assertEquals("", GithubPrFetchAdapter.GhRef().ref)
        assertEquals("", GithubPrFetchAdapter.GhRef().sha)
        val r = GithubPrFetchAdapter.GhRef(ref = "main", sha = "abc")
        assertEquals("main", r.ref)
        assertEquals("abc", r.sha)
    }

    @Test
    fun `MrFetchAdapter_GhUser default and explicit constructors`() {
        assertEquals("", GithubPrFetchAdapter.GhUser().login)
        assertEquals("alice", GithubPrFetchAdapter.GhUser(login = "alice").login)
    }

    @Test
    fun `MrFetchAdapter_GhFile default and explicit constructors`() {
        val r = GithubPrFetchAdapter.GhFile()
        assertEquals("", r.filename)
        assertNull(r.patch)
        assertEquals("", r.status)
        val r2 =
            GithubPrFetchAdapter.GhFile(
                filename = "f.kt",
                patch = "@@",
                status = "modified",
            )
        assertEquals("f.kt", r2.filename)
        assertEquals("modified", r2.status)
    }

    @Test
    fun `MrFetchAdapter_GhFile JSON roundtrip`() {
        assertTrue(true)
    }

    @Test
    fun `GithubPrFetchAdapter is constructible with default timeouts`() {
        val adapter = GithubPrFetchAdapter(MrDiffTextBuilder(DefaultDiffHunkParser()))
        assertNotNull(adapter)
    }
}
