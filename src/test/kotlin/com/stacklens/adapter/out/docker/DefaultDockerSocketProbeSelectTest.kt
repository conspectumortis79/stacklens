package com.stacklens.adapter.out.docker

import com.stacklens.shared.SocketChannelFactory
import com.stacklens.shared.SocketChannelSession
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DefaultDockerSocketProbeSelectTest {
    private val factory = mock<SocketChannelFactory>()
    private val session = mock<SocketChannelSession>()

    @Test
    fun `a slow socket is given another chance after each select round`() {
        var now = 0L
        whenever(factory.openAndConnect(eq("/tmp/docker.sock"), any())).thenReturn(session)
        var attempts = 0
        whenever(session.connect()).thenAnswer {
            attempts++
            if (attempts > 1) now = 9_999_999L
            false
        }
        val probe = DefaultDockerSocketProbe(factory, clock = { now })

        val status = probe.ping("/tmp/docker.sock")

        assertFalse(status.available)
        verify(session).select(any())
        verify(session).clearSelectedKeys()
        verify(session).close()
    }
}
