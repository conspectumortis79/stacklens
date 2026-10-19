package com.stacklens.infrastructure.db

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.sql.DriverManager

class H2ShutdownHookRuntimeSeamCoverageTest {
    @Test
    fun `hook registration is skipped while the jvm is already shutting down`() {
        val shuttingDown = mock<Runtime>()
        doThrow(IllegalStateException("Shutdown in progress"))
            .whenever(shuttingDown)
            .addShutdownHook(any())
        val hook = H2ShutdownHook()
        hook.runtime = shuttingDown

        hook.registerJvmHookInternal()
    }

    @Test
    fun `the shutdown hook thread is handed to the runtime`() {
        val runtime = mock<Runtime>()
        val threads = argumentCaptor<Thread>()
        val hook = H2ShutdownHook()
        hook.runtime = runtime

        hook.registerJvmHookInternal()

        verify(runtime).addShutdownHook(threads.capture())
        assertEquals("stacklens-h2-shutdown", threads.firstValue.name)
        assertFalse(threads.firstValue.isDaemon)
    }
}

class DefaultH2ShutdownExecutorCoverageTest {
    @Test
    fun `shutting the database down immediately discards the in-memory content`() {
        val url = "jdbc:h2:mem:stacklens_executor_cov;DB_CLOSE_DELAY=-1"
        DriverManager.getConnection(url, "sa", "").use { connection ->
            connection.createStatement().use { statement -> statement.execute("CREATE TABLE marker (id INT)") }
        }

        DefaultH2ShutdownExecutor().shutdown(url, "sa", "")

        DriverManager.getConnection(url, "sa", "").use { connection ->
            assertThrows(java.sql.SQLException::class.java) {
                connection.createStatement().execute("SELECT id FROM marker")
            }
        }
    }
}
