package com.stacklens.application.service.repo

import com.stacklens.application.port.out.i18n.I18nPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.net.UnknownHostException

class GitlabConnectivityErrorMapperTest {
    private val i18n: I18nPort = mock()
    private val mapper = GitlabConnectivityErrorMapper(i18n)

    @Test
    fun `friendlyErrorMessage returns null for unknown exception`() {
        val result = mapper.friendlyErrorMessage(RuntimeException("some other error"))
        assertNull(result)
    }

    @Test
    fun `friendlyErrorMessage handles UnknownHostException with host`() {
        val ex = UnknownHostException("gitlab.example.com: nodename nor servname provided")
        whenever(i18n.tFor(eq("de"), eq("config.be_gitlab_host_unresolved_with_host"), any()))
            .thenReturn("Cannot resolve gitlab.example.com")
        val result = mapper.friendlyErrorMessage(ex)
        assertNotNull(result)
        assertEquals("Cannot resolve gitlab.example.com", result)
    }

    @Test
    fun `friendlyErrorMessage handles UnknownHostException with empty host`() {
        val ex = UnknownHostException("")
        whenever(i18n.tFor(eq("de"), eq("config.be_gitlab_host_unresolved")))
            .thenReturn("Cannot resolve GitLab host")
        val result = mapper.friendlyErrorMessage(ex)
        assertNotNull(result)
        assertEquals("Cannot resolve GitLab host", result)
    }

    @Test
    fun `friendlyErrorMessage handles UnknownHostException with blank host`() {
        val ex = UnknownHostException("   ")
        whenever(i18n.tFor(eq("de"), eq("config.be_gitlab_host_unresolved")))
            .thenReturn("Cannot resolve GitLab host")
        val result = mapper.friendlyErrorMessage(ex)
        assertNotNull(result)
    }

    @Test
    fun `friendlyErrorMessage extracts first word of UnknownHostException message`() {
        val ex = UnknownHostException("gitlab.example.com additional info")
        whenever(i18n.tFor(eq("de"), eq("config.be_gitlab_host_unresolved_with_host"), any()))
            .thenReturn("Cannot resolve gitlab.example.com")
        val result = mapper.friendlyErrorMessage(ex)
        assertEquals("Cannot resolve gitlab.example.com", result)
    }

    @Test
    fun `friendlyErrorMessage walks cause chain to find UnknownHostException`() {
        val rootCause = UnknownHostException("gitlab.com")
        val middle = RuntimeException("wrapper", rootCause)
        val outer = IllegalStateException("outer", middle)
        whenever(i18n.tFor(eq("de"), eq("config.be_gitlab_host_unresolved_with_host"), any()))
            .thenReturn("Cannot resolve gitlab.com")
        val result = mapper.friendlyErrorMessage(outer)
        assertNotNull(result)
        assertEquals("Cannot resolve gitlab.com", result)
    }

    @Test
    fun `friendlyErrorMessage handles Connection refused`() {
        whenever(i18n.tFor(eq("de"), eq("config.be_gitlab_unreachable")))
            .thenReturn("GitLab is not reachable")
        val ex = RuntimeException("Connection refused: connect")
        val result = mapper.friendlyErrorMessage(ex)
        assertNotNull(result)
        assertEquals("GitLab is not reachable", result)
    }

    @Test
    fun `friendlyErrorMessage handles connect timed out`() {
        whenever(i18n.tFor(eq("de"), eq("config.be_gitlab_unreachable")))
            .thenReturn("GitLab is not reachable")
        val ex = RuntimeException("Connection connect timed out")
        val result = mapper.friendlyErrorMessage(ex)
        assertNotNull(result)
        assertEquals("GitLab is not reachable", result)
    }

    @Test
    fun `friendlyErrorMessage handles Failed to connect`() {
        whenever(i18n.tFor(eq("de"), eq("config.be_gitlab_unreachable")))
            .thenReturn("GitLab is not reachable")
        val ex = RuntimeException("Failed to connect to gitlab.com")
        val result = mapper.friendlyErrorMessage(ex)
        assertNotNull(result)
        assertEquals("GitLab is not reachable", result)
    }

    @Test
    fun `friendlyErrorMessage is case-insensitive for connection errors`() {
        whenever(i18n.tFor(eq("de"), eq("config.be_gitlab_unreachable")))
            .thenReturn("GitLab is not reachable")
        val ex = RuntimeException("CONNECTION REFUSED")
        val result = mapper.friendlyErrorMessage(ex)
        assertNotNull(result)
    }

    @Test
    fun `friendlyErrorMessage checks cause chain for connection errors`() {
        whenever(i18n.tFor(eq("de"), eq("config.be_gitlab_unreachable")))
            .thenReturn("GitLab is not reachable")
        val rootCause = RuntimeException("Connection refused")
        val wrapper = RuntimeException("wrapper", rootCause)
        val result = mapper.friendlyErrorMessage(wrapper)
        assertNotNull(result)
    }

    @Test
    fun `friendlyErrorMessage handles exception with null message`() {
        val ex = RuntimeException()
        ex.initCause(null)
        val result = mapper.friendlyErrorMessage(ex)
        assertNull(result)
    }

    @Test
    fun `friendlyErrorMessage prefers UnknownHostException over connection errors`() {
        val unknownHost = UnknownHostException("gitlab.com")
        val connectionError = RuntimeException("Connection refused")
        connectionError.initCause(unknownHost)
        whenever(i18n.tFor(eq("de"), eq("config.be_gitlab_host_unresolved_with_host"), any()))
            .thenReturn("Cannot resolve gitlab.com")
        val result = mapper.friendlyErrorMessage(connectionError)
        assertEquals("Cannot resolve gitlab.com", result)
    }

    @Test
    fun `friendlyErrorMessage returns null for empty cause chain`() {
        val ex = RuntimeException("unrelated error")
        val result = mapper.friendlyErrorMessage(ex)
        assertNull(result)
    }

    @Test
    fun `friendlyErrorMessage uses generic key when UnknownHostException has null message`() {
        val ex = UnknownHostException()
        whenever(i18n.tFor(eq("de"), eq("config.be_gitlab_host_unresolved")))
            .thenReturn("Cannot resolve GitLab host")
        val result = mapper.friendlyErrorMessage(ex)
        assertEquals("Cannot resolve GitLab host", result)
    }
}
