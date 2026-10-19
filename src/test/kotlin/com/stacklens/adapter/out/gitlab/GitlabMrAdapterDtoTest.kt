package com.stacklens.adapter.out.gitlab

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GitlabMrAdapterDtoTest {
    @Test
    fun `ProjectDetailResponse default and explicit`() {
        val r = GitlabMrAdapter.ProjectDetailResponse()
        assertEquals(0L, r.id)
        assertEquals("", r.name)
        assertEquals("main", r.default_branch)
        assertEquals("", r.path_with_namespace)
        assertEquals("", r.web_url)
        val r2 =
            GitlabMrAdapter.ProjectDetailResponse(
                id = 42L,
                name = "name",
                default_branch = "develop",
                path_with_namespace = "g/p",
                web_url = "https://gl/p",
            )
        assertEquals(42L, r2.id)
        assertEquals("develop", r2.default_branch)
    }

    @Test
    fun `MrResponse default and explicit`() {
        val r = GitlabMrAdapter.MrResponse()
        assertEquals(0L, r.iid)
        assertEquals("", r.web_url)
        val r2 = GitlabMrAdapter.MrResponse(iid = 5L, web_url = "https://gl/mr/5")
        assertEquals(5L, r2.iid)
        assertEquals("https://gl/mr/5", r2.web_url)
    }
}
