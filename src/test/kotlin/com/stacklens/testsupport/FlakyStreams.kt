package com.stacklens.testsupport

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class FlakyInputStream(
    private val payload: ByteArray = ByteArray(0),
    var failOnRead: Boolean = false,
    var failOnClose: Boolean = false,
) : InputStream() {
    private var position = 0

    var closeCalls: Int = 0
        private set

    override fun read(): Int {
        if (failOnRead) throw IOException("stream exploded")
        if (position >= payload.size) return -1
        return payload[position++].toInt() and 0xFF
    }

    override fun close() {
        closeCalls++
        if (failOnClose) throw IOException("close failed")
    }
}

class DelayedInputStream(
    private val chunks: List<ByteArray>,
    private val pauseFrom: Int = 1,
    private val pauseMillis: Long = 40L,
) : InputStream() {
    private var chunk = 0
    private var position = 0

    private val paused = BooleanArray(chunks.size)

    private fun advance() {
        while (chunk < chunks.size && position >= chunks[chunk].size) {
            chunk++
            position = 0
        }
    }

    private fun pauseIfNeeded() {
        if (chunk >= pauseFrom && !paused[chunk]) {
            paused[chunk] = true
            Thread.sleep(pauseMillis)
        }
    }

    override fun read(): Int {
        advance()
        if (chunk >= chunks.size) return -1
        pauseIfNeeded()
        return chunks[chunk][position++].toInt() and 0xFF
    }

    override fun read(
        b: ByteArray,
        off: Int,
        len: Int,
    ): Int {
        advance()
        if (chunk >= chunks.size) return -1
        pauseIfNeeded()
        val take = minOf(len, chunks[chunk].size - position)
        chunks[chunk].copyInto(b, off, position, position + take)
        position += take
        return take
    }
}

class FlakyOutputStream(
    var failOnWrite: Boolean = false,
    var failOnFlush: Boolean = false,
    var failOnClose: Boolean = false,
) : OutputStream() {
    val written = StringBuilder()

    var closeCalls: Int = 0
        private set

    override fun write(b: Int) {
        if (failOnWrite) throw IOException("pipe closed")
        written.append(b.toChar())
    }

    override fun write(
        b: ByteArray,
        off: Int,
        len: Int,
    ) {
        if (failOnWrite) throw IOException("pipe closed")
        written.append(String(b, off, len))
    }

    override fun flush() {
        if (failOnFlush) throw IOException("flush failed")
    }

    override fun close() {
        closeCalls++
        if (failOnClose) throw IOException("close failed")
    }
}
