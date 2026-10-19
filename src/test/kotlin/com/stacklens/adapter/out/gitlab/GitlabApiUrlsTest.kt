package com.stacklens.adapter.out.gitlab

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GitlabApiUrlsTest {
    @Test
    fun `groupProjects builds the membership URL without a group`() {
        assertEquals(
            "/api/v4/projects?membership=true&per_page=100&page=2",
            GitlabApiUrls.groupProjects(null, 2),
        )
    }

    @Test
    fun `groupProjects encodes nested group paths consistently`() {
        assertEquals(
            "/api/v4/groups/parent%2Fchild/projects?include_subgroups=true&per_page=100&page=3",
            GitlabApiUrls.groupProjects("parent/child", 3),
        )
    }

    @Test
    fun `repositoryTree builds the recursive tree URL`() {
        assertEquals(
            "/api/v4/projects/42/repository/tree?recursive=true&per_page=100&page=4&ref=feature%2Fbranch",
            GitlabApiUrls.repositoryTree(42L, "feature%2Fbranch", 4),
        )
    }

    @Test
    fun `fetchPages stops after a short page`() {
        val visited = mutableListOf<Int>()
        val result =
            GitlabApiUrls.fetchPages(5) { page ->
                visited += page
                if (page == 1) (1..100).toList() else listOf(101)
            }
        assertEquals(listOf(1, 2), visited)
        assertEquals(101, result.size)
    }

    @Test
    fun `fetchPages stops at the configured page ceiling`() {
        val visited = mutableListOf<Int>()
        val result =
            GitlabApiUrls.fetchPages(3) { page ->
                visited += page
                (1..100).toList()
            }
        assertEquals(listOf(1, 2, 3), visited)
        assertEquals(300, result.size)
    }

    @Test
    fun `fetchPages returns immediately for an empty first page`() {
        val result =
            GitlabApiUrls.fetchPages(GitlabApiUrls.UNLIMITED_PAGES) { page ->
                assertTrue(page == 1)
                emptyList<String>()
            }
        assertEquals(emptyList<String>(), result)
    }
}
