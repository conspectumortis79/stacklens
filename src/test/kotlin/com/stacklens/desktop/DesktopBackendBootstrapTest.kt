package com.stacklens.desktop

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class DesktopBackendBootstrapTest {
    @Test
    fun `resolveDataDir creates directory under home and returns it`() {
        val tmp = Files.createTempDirectory("home-").toAbsolutePath()
        try {
            val dir = DesktopBackendBootstrap.resolveDataDir(tmp.toString())
            assertTrue(Files.exists(dir))
            assertEquals(tmp.resolve(".stacklens/data"), dir.toAbsolutePath().normalize())
        } finally {
            Files.walk(tmp).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }

    @Test
    fun `resolveDataDir is idempotent on existing directory`() {
        val tmp = Files.createTempDirectory("home-").toAbsolutePath()
        try {
            val dir1 = DesktopBackendBootstrap.resolveDataDir(tmp.toString())
            val dir2 = DesktopBackendBootstrap.resolveDataDir(tmp.toString())
            assertEquals(dir1, dir2)
            assertTrue(Files.exists(dir1))
        } finally {
            Files.walk(tmp).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }

    @Test
    fun `h2FileUrl builds expected JDBC URL`() {
        val url = DesktopBackendBootstrap.h2FileUrl("/var/data")
        assertEquals(
            "jdbc:h2:file:/var/data/stacklens;MODE=PostgreSQL;WRITE_DELAY=500;DB_CLOSE_ON_EXIT=FALSE",
            url,
        )
    }

    @Test
    fun `h2FileUrl handles empty string`() {
        val url = DesktopBackendBootstrap.h2FileUrl("")
        assertEquals(
            "jdbc:h2:file:/stacklens;MODE=PostgreSQL;WRITE_DELAY=500;DB_CLOSE_ON_EXIT=FALSE",
            url,
        )
    }

    @Test
    fun `isDbLockException detects 90020 error code in message`() {
        val ex = RuntimeException("org.h2.message.DbException: Database may be already in use (90020)")
        assertTrue(DesktopBackendBootstrap.isDbLockException(ex))
    }

    @Test
    fun `isDbLockException detects Database already in use text`() {
        val ex = RuntimeException("Database may be already in use")
        assertTrue(DesktopBackendBootstrap.isDbLockException(ex))
    }

    @Test
    fun `isDbLockException walks the cause chain`() {
        val cause = RuntimeException("90020 lock detected")
        val ex = RuntimeException("wrapped", cause)
        assertTrue(DesktopBackendBootstrap.isDbLockException(ex))
    }

    @Test
    fun `isDbLockException returns false for unrelated error`() {
        val ex = RuntimeException("connection refused")
        assertFalse(DesktopBackendBootstrap.isDbLockException(ex))
    }

    @Test
    fun `isDbLockException handles null cause chain gracefully`() {
        val ex = RuntimeException("simple")
        assertFalse(DesktopBackendBootstrap.isDbLockException(ex))
    }

    @Test
    fun `isDbLockException handles null messages in chain`() {
        val cause = RuntimeException()
        val ex = RuntimeException("outer", cause)
        assertFalse(DesktopBackendBootstrap.isDbLockException(ex))
    }

    @Test
    fun `probePort returns null for unreachable host`() {
        val code = DesktopBackendBootstrap.probePort("http://127.0.0.1:1/")
        assertNull(code)
    }

    @Test
    fun `probePort returns code for reachable HTTP endpoint`() {
        val server =
            com.sun.net.httpserver.HttpServer
                .create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            ex.sendResponseHeaders(200, -1)
            ex.close()
        }
        server.start()
        try {
            val port = server.address.port
            val code = DesktopBackendBootstrap.probePort("http://127.0.0.1:$port/")
            assertEquals(200, code)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `probePort returns 404 for not-found endpoint`() {
        val server =
            com.sun.net.httpserver.HttpServer
                .create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            ex.sendResponseHeaders(404, -1)
            ex.close()
        }
        server.start()
        try {
            val port = server.address.port
            val code = DesktopBackendBootstrap.probePort("http://127.0.0.1:$port/")
            assertEquals(404, code)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `findRunningServer returns null when no port answers`() {
        val port =
            DesktopBackendBootstrap.findRunningServer(
                fromPort = 1,
                toPort = 5,
                host = "127.0.0.1",
                probe = { _ -> null },
            )
        assertNull(port)
    }

    @Test
    fun `findRunningServer returns first port with valid response code`() {
        val port =
            DesktopBackendBootstrap.findRunningServer(
                fromPort = 8081,
                toPort = 8083,
                host = "127.0.0.1",
                probe = { url ->
                    when {
                        url.endsWith(":8081/") -> 200
                        url.endsWith(":8082/") -> 503
                        url.endsWith(":8083/") -> 400
                        else -> null
                    }
                },
            )
        assertEquals(8081, port)
    }

    @Test
    fun `findRunningServer skips 5xx responses`() {
        val port =
            DesktopBackendBootstrap.findRunningServer(
                fromPort = 8081,
                toPort = 8083,
                host = "127.0.0.1",
                probe = { url ->
                    when {
                        url.endsWith(":8081/") -> 500
                        url.endsWith(":8082/") -> 200
                        else -> null
                    }
                },
            )
        assertEquals(8082, port)
    }

    @Test
    fun `findRunningServer returns null when only 5xx responses`() {
        val port =
            DesktopBackendBootstrap.findRunningServer(
                fromPort = 8081,
                toPort = 8082,
                host = "127.0.0.1",
                probe = { _ -> 500 },
            )
        assertNull(port)
    }

    @Test
    fun `findRunningServer stops scanning after first match`() {
        val calls = mutableListOf<Int>()
        val port =
            DesktopBackendBootstrap.findRunningServer(
                fromPort = 9000,
                toPort = 9010,
                host = "127.0.0.1",
                probe = { url ->
                    val p = url.substringAfterLast(":").substringBefore("/").toInt()
                    calls.add(p)
                    if (p == 9003) 200 else null
                },
            )
        assertEquals(9003, port)
        assertEquals(listOf(9000, 9001, 9002, 9003), calls)
    }

    @Test
    fun `findRunningServer handles connection refused gracefully`() {
        val port =
            DesktopBackendBootstrap.findRunningServer(
                fromPort = 1,
                toPort = 2,
                host = "127.0.0.1",
                probe = DesktopBackendBootstrap::probePort,
            )
        assertNull(port)
    }

    @Test
    fun `findRunningServer accepts 2xx and 4xx but rejects 5xx`() {
        assertEquals(
            9000,
            DesktopBackendBootstrap.findRunningServer(
                fromPort = 9000,
                toPort = 9000,
                probe = { _ -> 200 },
            ),
        )
        assertEquals(
            9001,
            DesktopBackendBootstrap.findRunningServer(
                fromPort = 9001,
                toPort = 9001,
                probe = { _ -> 404 },
            ),
        )
        assertEquals(
            9002,
            DesktopBackendBootstrap.findRunningServer(
                fromPort = 9002,
                toPort = 9002,
                probe = { _ -> 499 },
            ),
        )
        assertNull(
            DesktopBackendBootstrap.findRunningServer(
                fromPort = 9003,
                toPort = 9003,
                probe = { _ -> 500 },
            ),
        )
    }
}
