package com.stacklens.shared

import org.springframework.stereotype.Component
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.nio.channels.spi.SelectorProvider

interface SocketChannelFactory {
    fun openAndConnect(
        socketPath: String,
        deadline: Long,
    ): SocketChannelSession
}

class SocketChannelSession internal constructor(
    val channel: SocketChannel,
    val selector: Selector,
    val deadline: Long,
) {
    fun connect(): Boolean = channel.finishConnect()

    fun select(remaining: Long): Int = selector.select(remaining)

    fun clearSelectedKeys() = selector.selectedKeys().clear()

    fun writeRemaining(buf: ByteBuffer): Int = channel.write(buf)

    fun read(buf: ByteBuffer): Int = channel.read(buf)

    fun registerRead() = channel.register(selector, SelectionKey.OP_READ)

    fun close() {
        runCatching { channel.close() }
        runCatching { selector.close() }
    }
}

@Component
class DefaultSocketChannelFactory : SocketChannelFactory {
    override fun openAndConnect(
        socketPath: String,
        deadline: Long,
    ): SocketChannelSession {
        val addr = java.net.UnixDomainSocketAddress.of(socketPath)
        val ch = SocketChannel.open(java.net.StandardProtocolFamily.UNIX)
        ch.configureBlocking(false)
        val selector = SelectorProvider.provider().openSelector()
        ch.register(selector, SelectionKey.OP_CONNECT)
        ch.connect(addr)
        return SocketChannelSession(ch, selector, deadline)
    }
}
