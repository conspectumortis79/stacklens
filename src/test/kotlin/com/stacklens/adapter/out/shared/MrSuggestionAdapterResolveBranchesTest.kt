package com.stacklens.adapter.out.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.lang.reflect.InvocationTargetException
import java.net.URI

class MrSuggestionAdapterResolveBranchesTest {
    private val gitlabAdapter = GitlabMrSuggestionAdapter()
    private val githubAdapter = GithubPrSuggestionAdapter()

    @Test
    fun `resolveGitlabConfig returns matching config by id`() {
        val c1 = mock<com.stacklens.domain.model.GitlabConfig>()
        whenever(c1.id).thenReturn(1L)
        val c2 = mock<com.stacklens.domain.model.GitlabConfig>()
        whenever(c2.id).thenReturn(2L)
        val req =
            com.stacklens.application.port.out.MrSuggestionPort.PostRequest(
                mrUrl = "http://example.com/o/r/-/merge_requests/1",
                platform = "GitLab",
                gitConfigId = 2L,
                gitlabConfigs = listOf(c1, c2),
                githubConfigs = emptyList(),
                filePath = "x",
                lineNumber = 1,
                suggestedCode = "x",
                diffBaseSha = "x",
                diffStartSha = "x",
                diffHeadSha = "x",
            )
        val method =
            GitlabMrSuggestionAdapter::class.java.getDeclaredMethod(
                "resolveGitlabConfig",
                com.stacklens.application.port.out.MrSuggestionPort.PostRequest::class.java,
                URI::class.java,
            )
        method.isAccessible = true
        val result =
            method.invoke(gitlabAdapter, req, URI.create(req.mrUrl)) as com.stacklens.domain.model.GitlabConfig
        assertEquals(c2, result)
    }

    @Test
    fun `resolveGitlabConfig returns matching config by baseUrl`() {
        val c1 = mock<com.stacklens.domain.model.GitlabConfig>()
        whenever(c1.id).thenReturn(1L)
        whenever(c1.baseUrl).thenReturn("http://example.com")
        val req =
            com.stacklens.application.port.out.MrSuggestionPort.PostRequest(
                mrUrl = "http://example.com/o/r/-/merge_requests/1",
                platform = "GitLab",
                gitConfigId = null,
                gitlabConfigs = listOf(c1),
                githubConfigs = emptyList(),
                filePath = "x",
                lineNumber = 1,
                suggestedCode = "x",
                diffBaseSha = "x",
                diffStartSha = "x",
                diffHeadSha = "x",
            )
        val method =
            GitlabMrSuggestionAdapter::class.java.getDeclaredMethod(
                "resolveGitlabConfig",
                com.stacklens.application.port.out.MrSuggestionPort.PostRequest::class.java,
                URI::class.java,
            )
        method.isAccessible = true
        val result =
            method.invoke(gitlabAdapter, req, URI.create(req.mrUrl)) as com.stacklens.domain.model.GitlabConfig
        assertEquals(c1, result)
    }

    @Test
    fun `resolveGitlabConfig falls back to first config`() {
        val c1 = mock<com.stacklens.domain.model.GitlabConfig>()
        whenever(c1.id).thenReturn(1L)
        whenever(c1.baseUrl).thenReturn("http://other.com")
        val req =
            com.stacklens.application.port.out.MrSuggestionPort.PostRequest(
                mrUrl = "http://example.com/o/r/-/merge_requests/1",
                platform = "GitLab",
                gitConfigId = null,
                gitlabConfigs = listOf(c1),
                githubConfigs = emptyList(),
                filePath = "x",
                lineNumber = 1,
                suggestedCode = "x",
                diffBaseSha = "x",
                diffStartSha = "x",
                diffHeadSha = "x",
            )
        val method =
            GitlabMrSuggestionAdapter::class.java.getDeclaredMethod(
                "resolveGitlabConfig",
                com.stacklens.application.port.out.MrSuggestionPort.PostRequest::class.java,
                URI::class.java,
            )
        method.isAccessible = true
        val result =
            method.invoke(gitlabAdapter, req, URI.create(req.mrUrl)) as com.stacklens.domain.model.GitlabConfig
        assertEquals(c1, result)
    }

    @Test
    fun `resolveGithubToken returns token from matching config`() {
        val c1 = mock<com.stacklens.domain.model.GithubRepoConfig>()
        whenever(c1.id).thenReturn(1L)
        whenever(c1.accessToken).thenReturn("token-1")
        val c2 = mock<com.stacklens.domain.model.GithubRepoConfig>()
        whenever(c2.id).thenReturn(2L)
        whenever(c2.accessToken).thenReturn("token-2")
        val req =
            com.stacklens.application.port.out.MrSuggestionPort.PostRequest(
                mrUrl = "https://github.com/o/r/pull/1",
                platform = "GitHub",
                gitConfigId = 2L,
                gitlabConfigs = emptyList(),
                githubConfigs = listOf(c1, c2),
                filePath = "x",
                lineNumber = 1,
                suggestedCode = "x",
                diffBaseSha = "x",
                diffStartSha = "x",
                diffHeadSha = "x",
            )
        val method =
            GithubPrSuggestionAdapter::class.java.getDeclaredMethod(
                "resolveGithubToken",
                com.stacklens.application.port.out.MrSuggestionPort.PostRequest::class.java,
            )
        method.isAccessible = true
        val result = method.invoke(githubAdapter, req) as String?
        assertEquals("token-2", result)
    }

    @Test
    fun `resolveGithubToken falls back to first config token`() {
        val c1 = mock<com.stacklens.domain.model.GithubRepoConfig>()
        whenever(c1.id).thenReturn(1L)
        whenever(c1.accessToken).thenReturn("token-1")
        val req =
            com.stacklens.application.port.out.MrSuggestionPort.PostRequest(
                mrUrl = "https://github.com/o/r/pull/1",
                platform = "GitHub",
                gitConfigId = null,
                gitlabConfigs = emptyList(),
                githubConfigs = listOf(c1),
                filePath = "x",
                lineNumber = 1,
                suggestedCode = "x",
                diffBaseSha = "x",
                diffStartSha = "x",
                diffHeadSha = "x",
            )
        val method =
            GithubPrSuggestionAdapter::class.java.getDeclaredMethod(
                "resolveGithubToken",
                com.stacklens.application.port.out.MrSuggestionPort.PostRequest::class.java,
            )
        method.isAccessible = true
        val result = method.invoke(githubAdapter, req) as String?
        assertEquals("token-1", result)
    }

    @Test
    fun `resolveGithubToken does not guess another config token when an id was requested`() {
        val other = mock<com.stacklens.domain.model.GithubRepoConfig>()
        whenever(other.id).thenReturn(1L)
        whenever(other.accessToken).thenReturn("token-1")
        val req =
            com.stacklens.application.port.out.MrSuggestionPort.PostRequest(
                mrUrl = "https://github.com/o/r/pull/1",
                platform = "GitHub",
                gitConfigId = 99L,
                gitlabConfigs = emptyList(),
                githubConfigs = listOf(other),
                filePath = "x",
                lineNumber = 1,
                suggestedCode = "x",
                diffBaseSha = "x",
                diffStartSha = "x",
                diffHeadSha = "x",
            )
        val method =
            GithubPrSuggestionAdapter::class.java.getDeclaredMethod(
                "resolveGithubToken",
                com.stacklens.application.port.out.MrSuggestionPort.PostRequest::class.java,
            )
        method.isAccessible = true

        assertNull(method.invoke(githubAdapter, req) as String?)
    }

    @Test
    fun `resolveGitlabConfig refuses to guess between several unmatched configs`() {
        val first = mock<com.stacklens.domain.model.GitlabConfig>()
        whenever(first.id).thenReturn(1L)
        whenever(first.baseUrl).thenReturn("http://one.example")
        val second = mock<com.stacklens.domain.model.GitlabConfig>()
        whenever(second.id).thenReturn(2L)
        whenever(second.baseUrl).thenReturn("http://two.example")
        val mrUrl = "http://other.example/o/r/-/merge_requests/1"
        val req =
            com.stacklens.application.port.out.MrSuggestionPort.PostRequest(
                mrUrl = mrUrl,
                platform = "GitLab",
                gitConfigId = 99L,
                gitlabConfigs = listOf(first, second),
                githubConfigs = emptyList(),
                filePath = "x",
                lineNumber = 1,
                suggestedCode = "x",
                diffBaseSha = "x",
                diffStartSha = "x",
                diffHeadSha = "x",
            )
        val method =
            GitlabMrSuggestionAdapter::class.java.getDeclaredMethod(
                "resolveGitlabConfig",
                com.stacklens.application.port.out.MrSuggestionPort.PostRequest::class.java,
                URI::class.java,
            )
        method.isAccessible = true

        val failure =
            assertThrows(InvocationTargetException::class.java) {
                method.invoke(gitlabAdapter, req, URI.create(mrUrl))
            }

        assertEquals(
            "No GitLab config available for $mrUrl – 2 configs are registered and none matches the requested id or the URL host",
            failure.cause?.message,
        )
    }

    @Test
    fun `resolveGitlabConfig still resolves a lone config even when id and host differ`() {
        val only = mock<com.stacklens.domain.model.GitlabConfig>()
        whenever(only.id).thenReturn(1L)
        whenever(only.baseUrl).thenReturn("http://one.example")
        val mrUrl = "http://other.example/o/r/-/merge_requests/1"
        val req =
            com.stacklens.application.port.out.MrSuggestionPort.PostRequest(
                mrUrl = mrUrl,
                platform = "GitLab",
                gitConfigId = 99L,
                gitlabConfigs = listOf(only),
                githubConfigs = emptyList(),
                filePath = "x",
                lineNumber = 1,
                suggestedCode = "x",
                diffBaseSha = "x",
                diffStartSha = "x",
                diffHeadSha = "x",
            )
        val method =
            GitlabMrSuggestionAdapter::class.java.getDeclaredMethod(
                "resolveGitlabConfig",
                com.stacklens.application.port.out.MrSuggestionPort.PostRequest::class.java,
                URI::class.java,
            )
        method.isAccessible = true

        assertSame(only, method.invoke(gitlabAdapter, req, URI.create(mrUrl)))
    }

    @Test
    fun `resolveGithubToken yields null when the matched config carries no token`() {
        val matched = mock<com.stacklens.domain.model.GithubRepoConfig>()
        whenever(matched.id).thenReturn(5L)
        whenever(matched.accessToken).thenReturn(null)
        val req =
            com.stacklens.application.port.out.MrSuggestionPort.PostRequest(
                mrUrl = "https://github.com/o/r/pull/1",
                platform = "GitHub",
                gitConfigId = 5L,
                gitlabConfigs = emptyList(),
                githubConfigs = listOf(matched),
                filePath = "x",
                lineNumber = 1,
                suggestedCode = "x",
                diffBaseSha = "x",
                diffStartSha = "x",
                diffHeadSha = "x",
            )
        val method =
            GithubPrSuggestionAdapter::class.java.getDeclaredMethod(
                "resolveGithubToken",
                com.stacklens.application.port.out.MrSuggestionPort.PostRequest::class.java,
            )
        method.isAccessible = true

        assertNull(method.invoke(githubAdapter, req) as String?)
    }

    @Test
    fun `resolveGithubToken yields null when no github config is registered at all`() {
        val req =
            com.stacklens.application.port.out.MrSuggestionPort.PostRequest(
                mrUrl = "https://github.com/o/r/pull/1",
                platform = "GitHub",
                gitConfigId = 5L,
                gitlabConfigs = emptyList(),
                githubConfigs = emptyList(),
                filePath = "x",
                lineNumber = 1,
                suggestedCode = "x",
                diffBaseSha = "x",
                diffStartSha = "x",
                diffHeadSha = "x",
            )
        val method =
            GithubPrSuggestionAdapter::class.java.getDeclaredMethod(
                "resolveGithubToken",
                com.stacklens.application.port.out.MrSuggestionPort.PostRequest::class.java,
            )
        method.isAccessible = true

        assertNull(method.invoke(githubAdapter, req) as String?)
    }
}
