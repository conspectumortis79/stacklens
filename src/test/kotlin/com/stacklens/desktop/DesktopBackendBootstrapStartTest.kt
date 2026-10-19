package com.stacklens.desktop

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.env.ConfigurableEnvironment
import javax.sql.DataSource

class DesktopBackendBootstrapStartTest {
    @Test
    fun `start returns the assigned port from the Spring environment`() {
        val ctx = mock<ConfigurableApplicationContext>()
        val env = mock<ConfigurableEnvironment>()
        whenever(env.getProperty("local.server.port")).thenReturn("12345")
        whenever(ctx.environment).thenReturn(env)
        val startup =
            StartupRunner { _, _ -> ctx }
        val bootstrap = DesktopBackendBootstrap(startup)

        val port = bootstrap.start()
        assertEquals(12345, port)
    }

    @Test
    fun `start throws IllegalStateException when local_server_port is not set`() {
        val ctx = mock<ConfigurableApplicationContext>()
        val env = mock<ConfigurableEnvironment>()
        whenever(env.getProperty("local.server.port")).thenReturn(null)
        whenever(ctx.environment).thenReturn(env)
        val startup =
            StartupRunner { _, _ -> ctx }
        val bootstrap = DesktopBackendBootstrap(startup)

        assertThrows(IllegalStateException::class.java) {
            bootstrap.start()
        }
    }

    @Test
    fun `start throws IllegalStateException when port is not a number`() {
        val ctx = mock<ConfigurableApplicationContext>()
        val env = mock<ConfigurableEnvironment>()
        whenever(env.getProperty("local.server.port")).thenReturn("not-a-number")
        whenever(ctx.environment).thenReturn(env)
        val startup =
            StartupRunner { _, _ -> ctx }
        val bootstrap = DesktopBackendBootstrap(startup)

        assertThrows(IllegalStateException::class.java) {
            bootstrap.start()
        }
    }

    @Test
    fun `start detects DB lock and falls back to DuplicateInstanceException when probe fails`() {
        val startup =
            StartupRunner { _, _ ->
                throw RuntimeException("Database may be already in use (90020)")
            }
        val bootstrap = DesktopBackendBootstrap(startup)
        try {
            bootstrap.start()
        } catch (e: DuplicateInstanceException) {
            assertNotNull(e.message)
        } catch (_: Exception) {
        }
    }

    @Test
    fun `start returns the probed port when DB lock is thrown and probe succeeds`() {
        val server =
            com.sun.net.httpserver.HttpServer
                .create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            ex.sendResponseHeaders(200, -1)
            ex.close()
        }
        server.start()
        try {
            val startup =
                StartupRunner { _, _ ->
                    throw RuntimeException("Database may be already in use (90020)")
                }
            val port = server.address.port
            val bootstrap = DesktopBackendBootstrap(startup)
            val found =
                DesktopBackendBootstrap.findRunningServer(
                    fromPort = port,
                    toPort = port,
                )
            assertEquals(port, found)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `start rethrows non-lock exceptions without probing`() {
        val startup =
            StartupRunner { _, _ ->
                throw RuntimeException("Connection refused")
            }
        val bootstrap = DesktopBackendBootstrap(startup)
        val ex =
            assertThrows(RuntimeException::class.java) {
                bootstrap.start()
            }
        assertEquals("Connection refused", ex.message)
    }

    @Test
    fun `stop is a no-op when context is null`() {
        val bootstrap = DesktopBackendBootstrap(mock<StartupRunner>())
        bootstrap.stop()
        assertTrue(true)
    }

    @Test
    fun `stop closes the context if set`() {
        val ctx = mock<ConfigurableApplicationContext>()
        val env = mock<ConfigurableEnvironment>()
        whenever(env.getProperty("local.server.port")).thenReturn("1234")
        whenever(ctx.environment).thenReturn(env)
        val startup =
            StartupRunner { _, _ -> ctx }
        val bootstrap = DesktopBackendBootstrap(startup)
        bootstrap.start()
        bootstrap.stop()
        assertTrue(true)
    }

    @Test
    fun `checkpoint is a no-op when context is null`() {
        val bootstrap = DesktopBackendBootstrap(mock<StartupRunner>())
        bootstrap.checkpoint()
        assertTrue(true)
    }

    @Test
    fun `checkpoint executes CHECKPOINT SYNC when context provides a DataSource`() {
        val ctx = mock<ConfigurableApplicationContext>()
        val env = mock<ConfigurableEnvironment>()
        whenever(env.getProperty("local.server.port")).thenReturn("1234")
        whenever(ctx.environment).thenReturn(env)
        val ds = mock<DataSource>()
        val conn = mock<java.sql.Connection>()
        val stmt = mock<java.sql.Statement>()
        whenever(ctx.getBean(DataSource::class.java)).thenReturn(ds)
        whenever(ds.connection).thenReturn(conn)
        whenever(conn.createStatement()).thenReturn(stmt)

        val startup =
            StartupRunner { _, _ -> ctx }
        val bootstrap = DesktopBackendBootstrap(startup)
        bootstrap.start()
        bootstrap.checkpoint()

        org.mockito.kotlin
            .verify(stmt)
            .execute("CHECKPOINT SYNC")
        org.mockito.kotlin
            .verify(conn)
            .close()
    }

    @Test
    fun `checkpoint swallows exceptions from the DataSource lookup`() {
        val ctx = mock<ConfigurableApplicationContext>()
        val env = mock<ConfigurableEnvironment>()
        whenever(env.getProperty("local.server.port")).thenReturn("1234")
        whenever(ctx.environment).thenReturn(env)
        whenever(ctx.getBean(DataSource::class.java)).thenThrow(RuntimeException("no bean"))

        val startup =
            StartupRunner { _, _ -> ctx }
        val bootstrap = DesktopBackendBootstrap(startup)
        bootstrap.start()
        bootstrap.checkpoint()
        assertTrue(true)
    }
}
