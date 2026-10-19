package com.stacklens.desktop

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.context.ConfigurableApplicationContext
import javax.sql.DataSource

class DesktopBackendBootstrapProbeCoverageTest {
    @Test
    fun `the first port answering within the range wins`() {
        val codes = mapOf(8090 to null, 8091 to 600, 8092 to 100, 8093 to 404)

        assertEquals(8093, DesktopBackendBootstrap.findRunningServer(probe = { url -> codes[url.substringAfterLast(':').substringBefore('/').toInt()] }))
    }

    @Test
    fun `a scan without any answer reports no running server`() {
        assertNull(DesktopBackendBootstrap.findRunningServer(fromPort = 8090, toPort = 8092, probe = { null }))
        assertNull(DesktopBackendBootstrap.findRunningServer(fromPort = 8090, toPort = 8090, probe = { 999 }))
    }

    @Test
    fun `a url that is not served over http cannot be probed`() {
        assertNull(DesktopBackendBootstrap.probePort("ftp://example.invalid/"))
    }

    @Test
    fun `checkpoint without a running context does nothing`() {
        DesktopBackendBootstrap().checkpoint()
    }

    @Test
    fun `a failing checkpoint is swallowed`() {
        val bootstrap = DesktopBackendBootstrap()
        val context = mock<ConfigurableApplicationContext>()
        val dataSource = mock<DataSource>()
        whenever(context.getBean(DataSource::class.java)).thenReturn(dataSource)
        whenever(dataSource.connection).thenThrow(IllegalStateException("db is gone"))
        setContext(bootstrap, context)

        bootstrap.checkpoint()

        setContext(bootstrap, null)
    }

    private fun setContext(
        bootstrap: DesktopBackendBootstrap,
        context: Any?,
    ) {
        val field = DesktopBackendBootstrap::class.java.getDeclaredField("context")
        field.isAccessible = true
        field.set(bootstrap, context)
    }

    @Test
    fun `a successful checkpoint walks both use blocks`() {
        val bootstrap = DesktopBackendBootstrap()
        val context = mock<ConfigurableApplicationContext>()
        val dataSource = mock<DataSource>()
        val connection = mock<java.sql.Connection>()
        val statement = mock<java.sql.Statement>()
        whenever(context.getBean(DataSource::class.java)).thenReturn(dataSource)
        whenever(dataSource.connection).thenReturn(connection)
        whenever(connection.createStatement()).thenReturn(statement)
        whenever(statement.execute(any<String>())).thenReturn(true)
        setContext(bootstrap, context)

        bootstrap.checkpoint()
    }
}
