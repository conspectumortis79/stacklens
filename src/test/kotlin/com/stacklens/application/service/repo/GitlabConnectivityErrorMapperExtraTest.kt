package com.stacklens.application.service.repo

import com.stacklens.application.port.out.i18n.I18nPort
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.net.UnknownHostException

class GitlabConnectivityErrorMapperExtraTest {
    private val i18n = mock<I18nPort>()
    private val mapper = GitlabConnectivityErrorMapper(i18n)

    @BeforeEach
    fun setup() {
        whenever(i18n.tFor(any<String>(), any<String>(), any<Any>(), any<Any>(), any<Any>()))
            .thenAnswer { invocation -> invocation.arguments[1] as String }
        whenever(i18n.tFor(any<String>(), any<String>(), any<Any>(), any<Any>()))
            .thenAnswer { invocation -> invocation.arguments[1] as String }
        whenever(i18n.tFor(any<String>(), any<String>(), any<Any>()))
            .thenAnswer { invocation -> invocation.arguments[1] as String }
        whenever(i18n.tFor(any<String>(), any<String>()))
            .thenAnswer { invocation -> invocation.arguments[1] as String }
        whenever(i18n.t(any<String>(), any<Any>(), any<Any>()))
            .thenAnswer { invocation -> invocation.arguments[0] as String }
        whenever(i18n.t(any<String>(), any<Any>()))
            .thenAnswer { invocation -> invocation.arguments[0] as String }
    }

    @Test
    fun `friendlyErrorMessage returns null for unrelated exception`() {
        assertNull(mapper.friendlyErrorMessage(RuntimeException("boom")))
    }

    @Test
    fun `friendlyErrorMessage handles UnknownHost with host name`() {
        val msg = mapper.friendlyErrorMessage(UnknownHostException("gitlab.example.com: nodename nor servname provided"))
        assertNotNull(msg)
    }

    @Test
    fun `friendlyErrorMessage falls back to generic key when host missing`() {
        val msg = mapper.friendlyErrorMessage(UnknownHostException(""))
        assertNotNull(msg)
    }

    @Test
    fun `friendlyErrorMessage handles UnknownHost with blank first word`() {
        val msg = mapper.friendlyErrorMessage(UnknownHostException(" : no such host"))
        assertNotNull(msg)
    }

    @Test
    fun `friendlyErrorMessage detects Connection refused`() {
        val msg = mapper.friendlyErrorMessage(RuntimeException("Connection refused: localhost/127.0.0.1:443"))
        assertNotNull(msg)
    }

    @Test
    fun `friendlyErrorMessage detects connect timed out`() {
        val msg = mapper.friendlyErrorMessage(RuntimeException("connect timed out"))
        assertNotNull(msg)
    }

    @Test
    fun `friendlyErrorMessage detects Failed to connect`() {
        val msg = mapper.friendlyErrorMessage(RuntimeException("Failed to connect to gitlab"))
        assertNotNull(msg)
    }

    @Test
    fun `friendlyErrorMessage walks cause chain to find UnknownHost`() {
        val root = UnknownHostException("host.com: not found")
        val wrapper = RuntimeException("wrapper", root)
        val msg = mapper.friendlyErrorMessage(wrapper)
        assertNotNull(msg)
    }

    @Test
    fun `friendlyErrorMessage walks cause chain for unreachable`() {
        val root = RuntimeException("Connection refused")
        val wrapper = RuntimeException("outer", root)
        val msg = mapper.friendlyErrorMessage(wrapper)
        assertNotNull(msg)
    }

    @Test
    fun `friendlyErrorMessage returns null when no pattern matches`() {
        assertNull(mapper.friendlyErrorMessage(IllegalArgumentException("not network")))
    }

    @Test
    fun `friendlyErrorMessage handles null message on exception`() {
        assertNull(mapper.friendlyErrorMessage(RuntimeException()))
    }

    @Test
    fun `mapper instantiates with mock i18n`() {
        assertNotNull(mapper)
    }
}
