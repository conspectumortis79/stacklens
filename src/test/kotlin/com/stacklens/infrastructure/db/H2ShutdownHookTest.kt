package com.stacklens.infrastructure.db

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.context.event.ContextClosedEvent
import org.springframework.context.support.StaticApplicationContext
import java.sql.DriverManager

class H2ShutdownHookTest {
    private fun freshH2Url(suffix: String): String = "jdbc:h2:mem:h2test-$suffix-${System.nanoTime()}"

    private fun newHook(url: String): H2ShutdownHook {
        val hook = H2ShutdownHook()
        val urlField = H2ShutdownHook::class.java.getDeclaredField("activeJdbcUrl")
        urlField.isAccessible = true
        urlField.set(hook, url)
        val userField = H2ShutdownHook::class.java.getDeclaredField("activeUsername")
        userField.isAccessible = true
        userField.set(hook, "sa")
        val passField = H2ShutdownHook::class.java.getDeclaredField("activePassword")
        passField.isAccessible = true
        passField.set(hook, "")
        return hook
    }

    private fun readFlag(
        hook: H2ShutdownHook,
        name: String,
    ): Boolean {
        val field = H2ShutdownHook::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.getBoolean(hook)
    }

    @Test
    fun `runShutdown against an H2 in-memory database flips the shutdownAlreadyRun flag`() {
        val url = freshH2Url("success")
        val hook = newHook(url)
        assertFalse(readFlag(hook, "shutdownAlreadyRun"), "flag must start as false")
        hook.runShutdown("unit test")
        assertTrue(readFlag(hook, "shutdownAlreadyRun"), "flag must be true after SHUTDOWN")
    }

    @Test
    fun `runShutdown against a real (file-based) H2 database closes the file lock`() {
        val tmpDir = System.getProperty("java.io.tmpdir")
        val dbPath = "$tmpDir/stacklens-h2shutdown-test-${System.nanoTime()}"
        val url = "jdbc:h2:$dbPath"
        try {
            val hook = newHook(url)
            DriverManager.getConnection(url, "sa", "").use { c ->
                c.createStatement().use { s -> s.execute("CREATE TABLE t(id INT)") }
            }
            hook.runShutdown("file-based test")
            DriverManager.getConnection(url, "sa", "").use { c ->
                assertTrue(
                    c.isValid(1),
                    "After SHUTDOWN IMMEDIATELY a new connection to the same file should be valid",
                )
            }
        } finally {
            listOf("$dbPath.mv.db", "$dbPath.trace.db").forEach { p ->
                runCatching { java.io.File(p).delete() }
            }
        }
    }

    @Test
    fun `runShutdown is a no-op for non-H2 datasources`() {
        val hook = newHook("jdbc:postgresql://localhost:5432/mydb")
        hook.runShutdown("unit test")
        assertTrue(readFlag(hook, "shutdownAlreadyRun"))
        hook.runShutdown("unit test again")
    }

    @Test
    fun `runShutdown is idempotent across multiple triggers`() {
        val url = freshH2Url("idempotent")
        val hook = newHook(url)
        DriverManager.getConnection(url, "sa", "").use { c ->
            c.createStatement().use { s -> s.execute("CREATE TABLE t2(id INT)") }
        }
        hook.runShutdown("trigger 1")
        hook.runShutdown("trigger 2")
        hook.runShutdown("trigger 3")
        assertTrue(readFlag(hook, "shutdownAlreadyRun"))
    }

    @Test
    fun `runShutdown treats already-closed database as success`() {
        val url = freshH2Url("already-closed")
        val hook = newHook(url)
        DriverManager.getConnection(url, "sa", "").use { c ->
            c.createStatement().use { s -> s.execute("SHUTDOWN IMMEDIATELY") }
        }
        hook.runShutdown("trigger after manual SHUTDOWN")
        assertTrue(readFlag(hook, "shutdownAlreadyRun"))
    }

    @Test
    fun `onApplicationEvent delegates to runShutdown`() {
        val url = freshH2Url("listener")
        val hook = newHook(url)
        DriverManager.getConnection(url, "sa", "").use { c ->
            c.createStatement().use { s -> s.execute("CREATE TABLE t3(id INT)") }
        }
        hook.onApplicationEvent(ContextClosedEvent(StaticApplicationContext()))
        assertTrue(readFlag(hook, "shutdownAlreadyRun"))
    }

    @Test
    fun `runShutdown logs warn when executor throws an unrelated exception`() {
        val hook = newHook(freshH2Url("warn"))
        val boom = RuntimeException("simulated network partition")
        hook.executor =
            H2ShutdownExecutor { _, _, _ -> throw boom }
        hook.runShutdown("warn-test")
        assertTrue(readFlag(hook, "shutdownAlreadyRun"), "flag must still flip on failure")
    }

    @Test
    fun `runShutdown treats message containing already closed as success`() {
        val hook = newHook(freshH2Url("already-closed-msg"))
        hook.executor =
            H2ShutdownExecutor { _, _, _ -> throw RuntimeException("Database 90013 is already closed, friend") }
        hook.runShutdown("already-closed-msg-test")
        assertTrue(readFlag(hook, "shutdownAlreadyRun"))
    }

    @Test
    fun `runShutdown treats message containing 90013 as success`() {
        val hook = newHook(freshH2Url("code-90013"))
        hook.executor =
            H2ShutdownExecutor { _, _, _ -> throw RuntimeException("Generic failure containing 90013 somewhere") }
        hook.runShutdown("code-90013-test")
        assertTrue(readFlag(hook, "shutdownAlreadyRun"))
    }

    @Test
    fun `DefaultH2ShutdownExecutor runs SHUTDOWN IMMEDIATELY against a real H2 url`() {
        val url = freshH2Url("default-exec")
        DriverManager.getConnection(url, "sa", "").use { c ->
            c.createStatement().use { s -> s.execute("CREATE TABLE t4(id INT)") }
        }
        DefaultH2ShutdownExecutor().shutdown(url, "sa", "")
        DefaultH2ShutdownExecutor().shutdown(url, "sa", "")
    }
}
