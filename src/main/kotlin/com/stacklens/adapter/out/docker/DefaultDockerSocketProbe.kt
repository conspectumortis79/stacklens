package com.stacklens.adapter.out.docker

import com.stacklens.application.port.out.docker.DockerSocketProbe
import com.stacklens.domain.docker.DockerStatus
import com.stacklens.domain.util.exceptionDiagnostic
import com.stacklens.shared.SocketChannelFactory
import com.stacklens.shared.SocketChannelSession
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.nio.ByteBuffer

@Component
class DefaultDockerSocketProbe(
    private val factory: SocketChannelFactory,
    private val clock: () -> Long = System::currentTimeMillis,
) : DockerSocketProbe {
    private val log = LoggerFactory.getLogger(DefaultDockerSocketProbe::class.java)

    override fun ping(socketPath: String): DockerStatus {
        val deadline = clock() + PING_TIMEOUT_MS
        val session =
            try {
                factory.openAndConnect(socketPath, deadline)
            } catch (e: Exception) {
                return DockerStatus(false, "socket ping error for $socketPath: ${exceptionDiagnostic(e)}")
            }
        return try {
            if (!waitForConnect(session, deadline)) {
                return DockerStatus(false, "socket connect timed out: $socketPath")
            }
            if (!writeRequest(session, deadline)) {
                return DockerStatus(false, "socket write timed out: $socketPath")
            }
            val line = readResponse(session, deadline)
            if (line == null) return DockerStatus(false, "socket read timed out: $socketPath")
            if (line.startsWith(SUCCESS_PREFIX)) {
                DockerStatus(true, "socket ping ok: $socketPath")
            } else {
                DockerStatus(false, "unexpected response from $socketPath: ${line.take(RESPONSE_ECHO_CHARS)}")
            }
        } catch (e: Exception) {
            log.debug("[DockerSocketProbe] ping failed for {}: {}", socketPath, e.message)
            DockerStatus(false, "socket ping error for $socketPath: ${exceptionDiagnostic(e)}")
        } finally {
            session.close()
        }
    }

    private fun waitForConnect(
        session: SocketChannelSession,
        deadline: Long,
    ): Boolean {
        while (!session.connect()) {
            val remaining = deadline - clock()
            if (remaining <= 0) return false
            session.select(remaining)
            session.clearSelectedKeys()
        }
        return true
    }

    private fun writeRequest(
        session: SocketChannelSession,
        deadline: Long,
    ): Boolean {
        val reqBuf = ByteBuffer.wrap(PING_REQUEST.toByteArray(Charsets.UTF_8))
        while (reqBuf.hasRemaining()) {
            if (clock() > deadline) return false
            session.writeRemaining(reqBuf)
        }
        return true
    }

    private fun readResponse(
        session: SocketChannelSession,
        deadline: Long,
    ): String? {
        session.registerRead()
        val buf = ByteBuffer.allocate(READ_BUFFER_BYTES)
        while (buf.position() < RESPONSE_PREFIX_BYTES) {
            val remaining = deadline - clock()
            if (remaining <= 0) return null
            if (session.select(remaining) == 0) continue
            session.clearSelectedKeys()
            val n = session.read(buf)
            if (n < 0) break
        }
        buf.flip()
        return Charsets.UTF_8.decode(buf).toString()
    }

    private companion object {
        const val PING_TIMEOUT_MS = 2_000L
        const val PING_REQUEST = "GET /_ping HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n"
        const val SUCCESS_PREFIX = "HTTP/1.1 200"
        const val RESPONSE_ECHO_CHARS = 32
        const val RESPONSE_PREFIX_BYTES = 16
        const val READ_BUFFER_BYTES = 64
    }
}
