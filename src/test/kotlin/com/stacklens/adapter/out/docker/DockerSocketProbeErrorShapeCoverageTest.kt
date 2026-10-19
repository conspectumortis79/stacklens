package com.stacklens.adapter.out.docker

import com.stacklens.shared.SocketChannelFactory
import com.stacklens.shared.SocketChannelSession
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer

class DockerSocketProbeErrorShapeCoverageTest {
    private val factory = mock<SocketChannelFactory>()
    private val session = mock<SocketChannelSession>()

    private fun probe(now: Long = 0L) = DefaultDockerSocketProbe(factory, clock = { now })

    @Test
    fun `a socket that cannot be opened reports its class when the failure is silent`() {
        whenever(factory.openAndConnect(eq("/tmp/a.sock"), any())).thenThrow(IllegalStateException())

        val status = probe().ping("/tmp/a.sock")

        assertTrue(status.reason.endsWith("IllegalStateException"), status.reason)
    }

    @Test
    fun `a socket that cannot be opened quotes the failure`() {
        whenever(factory.openAndConnect(eq("/tmp/b.sock"), any())).thenThrow(IllegalStateException("no such file"))

        val status = probe().ping("/tmp/b.sock")

        assertTrue(status.reason.contains("no such file"), status.reason)
    }

    @Test
    fun `a session that breaks later is reported the same way`() {
        whenever(factory.openAndConnect(any(), any())).thenReturn(session)
        whenever(session.connect()).thenThrow(IllegalStateException())

        val status = probe().ping("/tmp/c.sock")

        assertTrue(status.reason.endsWith("IllegalStateException"), status.reason)
    }

    @Test
    fun `a session that breaks after the request only quotes the message`() {
        whenever(factory.openAndConnect(any(), any())).thenReturn(session)
        whenever(session.connect()).thenReturn(true)
        whenever(session.writeRemaining(any())).thenAnswer { invocation ->
            val buffer = invocation.getArgument<ByteBuffer>(0)
            buffer.position(buffer.limit())
            1
        }
        whenever(session.select(Mockito.anyLong())).thenReturn(1)
        whenever(session.read(any())).thenThrow(IllegalStateException("reset by peer"))

        val status = probe(5_000L).ping("/tmp/d.sock")

        assertTrue(status.reason.contains("reset by peer"), status.reason)
    }

    @Test
    fun `a socket that hangs up mid answer ends the read loop`() {
        whenever(factory.openAndConnect(any(), any())).thenReturn(session)
        whenever(session.connect()).thenReturn(true)
        whenever(session.writeRemaining(any())).thenAnswer { invocation ->
            val buffer = invocation.getArgument<ByteBuffer>(0)
            buffer.position(buffer.limit())
            1
        }
        whenever(session.select(Mockito.anyLong())).thenReturn(1)
        whenever(session.read(any())).thenReturn(-1)

        val status = probe(5_000L).ping("/tmp/e.sock")

        assertTrue(status.reason.startsWith("unexpected response"), status.reason)
    }
}
