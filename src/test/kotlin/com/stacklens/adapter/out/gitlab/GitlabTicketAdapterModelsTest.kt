package com.stacklens.adapter.out.gitlab

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GitlabTicketAdapterModelsTest {
    @Test
    fun `JiraBridgeGitlabProjectDto default and explicit`() {
        val r = JiraBridgeGitlabProjectDto()
        assertEquals(0L, r.id)
        assertEquals("", r.name)
        assertEquals("", r.pathWithNamespace)
        val r2 = JiraBridgeGitlabProjectDto(id = 1L, name = "n", pathWithNamespace = "g/p")
        assertEquals(1L, r2.id)
        assertEquals("g/p", r2.pathWithNamespace)
    }

    @Test
    fun `JiraBridgeGitlabProjectDetailDto default and explicit`() {
        val r = JiraBridgeGitlabProjectDetailDto()
        assertEquals(0L, r.id)
        assertEquals("main", r.defaultBranch)
        val r2 = JiraBridgeGitlabProjectDetailDto(id = 1L, defaultBranch = "develop")
        assertEquals("develop", r2.defaultBranch)
    }

    @Test
    fun `JiraBridgeGitlabMrDto default and explicit`() {
        val r = JiraBridgeGitlabMrDto()
        assertEquals(0L, r.id)
        assertEquals(0L, r.iid)
        assertEquals("", r.title)
        assertNull(r.description)
        assertEquals("", r.state)
        assertEquals("", r.webUrl)
        val r2 =
            JiraBridgeGitlabMrDto(
                id = 1L,
                iid = 5L,
                title = "T",
                description = "D",
                state = "opened",
                webUrl = "https://gl/mr/5",
            )
        assertEquals("https://gl/mr/5", r2.webUrl)
    }

    @Test
    fun `JiraBridgeGitlabMrChangesResponse default and explicit`() {
        val r = JiraBridgeGitlabMrChangesResponse()
        assertEquals(emptyList<JiraBridgeGitlabFileChange>(), r.changes)
        val r2 = JiraBridgeGitlabMrChangesResponse(changes = listOf(JiraBridgeGitlabFileChange()))
        assertEquals(1, r2.changes.size)
    }

    @Test
    fun `GitlabTreeEntry default and explicit`() {
        val r = GitlabTreeEntry()
        assertEquals("", r.id)
        assertEquals("", r.name)
        assertEquals("", r.type)
        assertEquals("", r.path)
        val r2 = GitlabTreeEntry(id = "i", name = "n", type = "blob", path = "p")
        assertEquals("p", r2.path)
    }

    @Test
    fun `JiraBridgeGitlabFileChange default and explicit`() {
        val r = JiraBridgeGitlabFileChange()
        assertEquals("", r.oldPath)
        assertEquals("", r.newPath)
        assertEquals("", r.diff)
        val r2 = JiraBridgeGitlabFileChange(oldPath = "o", newPath = "n", diff = "@@")
        assertEquals("n", r2.newPath)
    }
}
