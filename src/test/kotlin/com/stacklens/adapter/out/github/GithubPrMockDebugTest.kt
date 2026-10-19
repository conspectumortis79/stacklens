package com.stacklens.adapter.out.github

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doReturn
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.springframework.web.reactive.function.client.WebClient

class GithubPrMockDebugTest {
    @Test
    fun `mock returns stubbed URL for openPullRequest`() {
        val mockPr = mock<GithubPullRequestClient>()
        doReturn("https://test/pr/1")
            .`when`(mockPr)
            .openPullRequest(any(), any(), any(), any(), any(), any(), any())
        val result =
            mockPr.openPullRequest(
                WebClient.builder().build(),
                "octo",
                "cat",
                "title",
                "head",
                "main",
                "body",
            )
        assertEquals("https://test/pr/1", result)
    }

    @Test
    fun `mock returns true for createBranch`() {
        val mockPr = mock<GithubPullRequestClient>()
        doReturn(true).`when`(mockPr).createBranch(any(), any(), any(), any(), any())
        val result =
            mockPr.createBranch(
                WebClient.builder().build(),
                "octo",
                "cat",
                "sha",
                "branch",
            )
        assertEquals(true, result)
    }
}
