package com.stacklens.application.service
import com.stacklens.adapter.out.docker.DefaultDockerSocketProbe
import com.stacklens.shared.SocketChannelFactory
import com.stacklens.shared.SocketChannelSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.util.concurrent.atomic.AtomicInteger

class DefaultDockerSocketProbeFullTest {
    private fun fakeSession(
        connectReturns: List<Boolean> = listOf(true),
        selectReturns: List<Int> = listOf(1),
        readReturns: List<Int> = listOf(16, -1),
    ): SocketChannelSession {
        val connectIdx = AtomicInteger(0)
        val selectIdx = AtomicInteger(0)
        val readIdx = AtomicInteger(0)
        val session = mock<SocketChannelSession>()
        whenever(session.connect()).thenAnswer {
            val i = connectIdx.getAndIncrement()
            if (i < connectReturns.size) connectReturns[i] else connectReturns.last()
        }
        whenever(session.select(any<Long>())).thenAnswer {
            val i = selectIdx.getAndIncrement()
            if (i < selectReturns.size) selectReturns[i] else selectReturns.last()
        }
        whenever(session.read(any<ByteBuffer>())).thenAnswer {
            val i = readIdx.getAndIncrement()
            if (i < readReturns.size) readReturns[i] else readReturns.last()
        }
        return session
    }

    private fun probe(
        session: SocketChannelSession?,
        openThrows: Throwable? = null,
        clock: () -> Long = { 0L },
    ): DefaultDockerSocketProbe {
        val factory =
            object : SocketChannelFactory {
                override fun openAndConnect(
                    socketPath: String,
                    deadline: Long,
                ): SocketChannelSession {
                    if (openThrows != null) throw openThrows
                    return session ?: error("no session provided")
                }
            }
        return DefaultDockerSocketProbe(factory, clock)
    }

    @Test
    fun `ping returns failure when openAndConnect throws`() {
        val result =
            probe(session = null, openThrows = java.io.IOException("cannot open"))
                .ping("/var/run/docker.sock")
        assertFalse(result.available)
        assertTrue(result.reason.contains("socket ping error"))
    }

    @Test
    fun `ping returns failure when connect times out`() {
        val session = fakeSession(connectReturns = listOf(false))

        val tick =
            java.util.concurrent.atomic
                .AtomicLong(0L)
        val p =
            probe(
                session = session,
                clock = { tick.addAndGet(3_000L) - 3_000L },
            )
        val result = p.ping("/sock")
        assertFalse(result.available)
        assertTrue(result.reason.contains("socket connect timed out"))
    }

    @Test
    fun `ping returns failure when write times out`() {
        var firstCall = true
        val session = fakeSession(connectReturns = listOf(true))
        val p =
            probe(
                session = session,
                clock = {
                    if (firstCall) {
                        firstCall = false
                        5_000L
                    } else {
                        20_000L
                    }
                },
            )
        val result = p.ping("/sock")
        assertFalse(result.available)
        assertTrue(result.reason.contains("socket write timed out"))
    }

    @Test
    fun `ping returns failure when read times out`() {
        val fullRequestLen = "GET /_ping HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".length
        val tick =
            java.util.concurrent.atomic
                .AtomicLong(0L)
        val session =
            fakeSession(
                connectReturns = listOf(true),
                selectReturns = listOf(1, 0),
            )
        whenever(session.writeRemaining(any<ByteBuffer>())).thenReturn(fullRequestLen)
        whenever(session.read(any<ByteBuffer>())).thenReturn(0)
        val p =
            probe(
                session = session,
                clock = {
                    val n = tick.incrementAndGet()
                    if (n <= 5) 0L else 5_000L
                },
            )
        val result = p.ping("/sock")

        assertFalse(result.available)
        assertTrue(result.reason.contains("timed out"))
    }

    @Test
    fun `ping returns failure when the IO loop throws`() {
        val session = mock<SocketChannelSession>()
        whenever(session.connect()).thenReturn(true)
        whenever(session.writeRemaining(any<ByteBuffer>())).thenThrow(RuntimeException("io boom"))
        val p = probe(session = session, clock = { 0L })
        val result = p.ping("/sock")
        assertFalse(result.available)
        assertTrue(result.reason.contains("io boom") || result.reason.contains("RuntimeException"))
    }

    @Test
    fun `SocketChannelSession connect delegates to channel finishConnect`() {
        val fakeSelector = mock<Selector>()
        val fakeChannel = mock<SocketChannel>()
        val s = SocketChannelSession(fakeChannel, fakeSelector, 0L)
        whenever(fakeChannel.finishConnect()).thenReturn(true)
        assertTrue(s.connect())
    }

    @Test
    fun `SocketChannelSession select delegates to selector select`() {
        val fakeSelector = mock<Selector>()
        val fakeChannel = mock<SocketChannel>()
        val s = SocketChannelSession(fakeChannel, fakeSelector, 0L)
        whenever(fakeSelector.select(123L)).thenReturn(7)
        assertEquals(7, s.select(123L))
    }

    @Test
    fun `SocketChannelSession clearSelectedKeys delegates to selector selectedKeys clear`() {
        val fakeSelector = mock<Selector>()
        val fakeChannel = mock<SocketChannel>()
        val keys = java.util.HashSet<SelectionKey>()
        whenever(fakeSelector.selectedKeys()).thenReturn(keys)
        val s = SocketChannelSession(fakeChannel, fakeSelector, 0L)
        s.clearSelectedKeys()
        assertTrue(keys.isEmpty())
    }

    @Test
    fun `SocketChannelSession writeRemaining delegates to channel write`() {
        val fakeSelector = mock<Selector>()
        val fakeChannel = mock<SocketChannel>()
        val s = SocketChannelSession(fakeChannel, fakeSelector, 0L)
        val buf = ByteBuffer.wrap("hello".toByteArray())
        whenever(fakeChannel.write(buf)).thenReturn(5)
        assertEquals(5, s.writeRemaining(buf))
    }

    @Test
    fun `SocketChannelSession read delegates to channel read`() {
        val fakeSelector = mock<Selector>()
        val fakeChannel = mock<SocketChannel>()
        val s = SocketChannelSession(fakeChannel, fakeSelector, 0L)
        val buf = ByteBuffer.allocate(8)
        whenever(fakeChannel.read(buf)).thenReturn(4)
        assertEquals(4, s.read(buf))
    }

    @Test
    fun `SocketChannelSession registerRead registers OP_READ`() {
        val fakeSelector = mock<Selector>()
        val fakeChannel = mock<SocketChannel>()
        val s = SocketChannelSession(fakeChannel, fakeSelector, 0L)
        s.registerRead()
        org.mockito.kotlin
            .verify(fakeChannel)
            .register(fakeSelector, SelectionKey.OP_READ)
    }

    @Test
    fun `SocketChannelSession close swallows exceptions from channel and selector`() {
        val fakeSelector = mock<Selector>()
        val fakeChannel = mock<SocketChannel>()
        whenever(fakeChannel.close()).thenThrow(java.io.IOException("x"))
        whenever(fakeSelector.close()).thenThrow(java.io.IOException("y"))
        val s = SocketChannelSession(fakeChannel, fakeSelector, 0L)
        s.close()
    }
}
