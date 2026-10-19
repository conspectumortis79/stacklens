package com.stacklens.infrastructure.db

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class H2ShutdownHookCoverageTest {
    private fun hook(
        url: String? = null,
        username: String? = "sa",
        password: String? = "",
    ): H2ShutdownHook {
        val h = H2ShutdownHook()
        val urlField = H2ShutdownHook::class.java.getDeclaredField("activeJdbcUrl").apply { isAccessible = true }
        val userField = H2ShutdownHook::class.java.getDeclaredField("activeUsername").apply { isAccessible = true }
        val passField = H2ShutdownHook::class.java.getDeclaredField("activePassword").apply { isAccessible = true }
        urlField.set(h, url)
        userField.set(h, username)
        passField.set(h, password)
        return h
    }

    @Test
    fun `runShutdown is a no-op when the URL is null`() {
        val h = hook(url = null)
        h.runShutdown("test")
        val flagField = H2ShutdownHook::class.java.getDeclaredField("shutdownAlreadyRun").apply { isAccessible = true }
        assertTrue(flagField.getBoolean(h))
    }

    @Test
    fun `runShutdown is a no-op when the URL is not H2`() {
        var called = false
        val h =
            hook(url = "jdbc:postgresql://localhost/db").apply {
                executor =
                    H2ShutdownExecutor { _, _, _ -> called = true }
            }
        h.runShutdown("test")
        assertFalse(called)
    }

    @Test
    fun `runShutdown invokes the executor for an H2 URL`() {
        var calledWith: Triple<String, String?, String?>? = null
        val h =
            hook(url = "jdbc:h2:mem:test", username = "sa", password = "").apply {
                executor =
                    H2ShutdownExecutor { u, user, pass -> calledWith = Triple(u, user, pass) }
            }
        h.runShutdown("trigger")
        assertEquals(Triple("jdbc:h2:mem:test", "sa", ""), calledWith)
    }

    @Test
    fun `runShutdown swallows SQLException when message contains 90013`() {
        val h =
            hook(url = "jdbc:h2:mem:test").apply {
                executor =
                    H2ShutdownExecutor { _, _, _ -> throw RuntimeException("90013: database already closed") }
            }
        h.runShutdown("test")
        val flagField = H2ShutdownHook::class.java.getDeclaredField("shutdownAlreadyRun").apply { isAccessible = true }
        assertTrue(flagField.getBoolean(h))
    }

    @Test
    fun `runShutdown swallows SQLException when message contains already closed`() {
        val h =
            hook(url = "jdbc:h2:mem:test").apply {
                executor =
                    H2ShutdownExecutor { _, _, _ -> throw RuntimeException("Database already closed") }
            }
        h.runShutdown("test")
        val flagField = H2ShutdownHook::class.java.getDeclaredField("shutdownAlreadyRun").apply { isAccessible = true }
        assertTrue(flagField.getBoolean(h))
    }

    @Test
    fun `runShutdown swallows other exceptions but still sets the flag`() {
        val h =
            hook(url = "jdbc:h2:mem:test").apply {
                executor =
                    H2ShutdownExecutor { _, _, _ -> throw RuntimeException("some other error") }
            }
        h.runShutdown("test")
        val flagField = H2ShutdownHook::class.java.getDeclaredField("shutdownAlreadyRun").apply { isAccessible = true }
        assertTrue(flagField.getBoolean(h))
    }

    @Test
    fun `runShutdown is idempotent — second invocation is skipped`() {
        var calls = 0
        val h =
            hook(url = "jdbc:h2:mem:test").apply {
                executor =
                    H2ShutdownExecutor { _, _, _ -> calls++ }
            }
        h.runShutdown("first")
        h.runShutdown("second")
        assertEquals(1, calls)
    }

    @Test
    fun `captureConfig reads from envLookup first`() {
        val h = H2ShutdownHook()
        h.captureConfig { name ->
            when (name) {
                "SPRING_DATASOURCE_URL" -> "jdbc:h2:mem:env"
                "SPRING_DATASOURCE_USERNAME" -> "envuser"
                "SPRING_DATASOURCE_PASSWORD" -> "envpass"
                else -> null
            }
        }
        assertEquals("jdbc:h2:mem:env", getField(h, "activeJdbcUrl"))
        assertEquals("envuser", getField(h, "activeUsername"))
        assertEquals("envpass", getField(h, "activePassword"))
    }

    @Test
    fun `captureConfig defaults username to sa and password to empty when env is missing`() {
        val h = H2ShutdownHook()
        withClearedProperties {
            h.captureConfig { _ -> null }
            assertNull(getField(h, "activeJdbcUrl"))
            assertEquals("sa", getField(h, "activeUsername"))
            assertEquals("", getField(h, "activePassword"))
        }
    }

    @Test
    fun `captureConfig falls back to system properties when envLookup returns null`() {
        val h = H2ShutdownHook()
        withClearedProperties {
            System.setProperty("spring.datasource.url", "jdbc:h2:mem:sys")
            System.setProperty("spring.datasource.username", "sysuser")
            h.captureConfig { _ -> null }
            assertEquals("jdbc:h2:mem:sys", getField(h, "activeJdbcUrl"))
            assertEquals("sysuser", getField(h, "activeUsername"))
        }
    }

    @Test
    fun `isAlreadyClosedError returns true for SQLState 90013`() {
        val h = H2ShutdownHook()
        assertTrue(h.isAlreadyClosedError(RuntimeException("90013: database is already closed")))
    }

    @Test
    fun `isAlreadyClosedError returns true for already closed message`() {
        val h = H2ShutdownHook()
        assertTrue(h.isAlreadyClosedError(RuntimeException("Database already closed")))
    }

    @Test
    fun `isAlreadyClosedError returns true case-insensitively`() {
        val h = H2ShutdownHook()
        assertTrue(h.isAlreadyClosedError(RuntimeException("ALREADY CLOSED")))
    }

    @Test
    fun `isAlreadyClosedError returns false for unrelated errors`() {
        val h = H2ShutdownHook()
        assertFalse(h.isAlreadyClosedError(RuntimeException("connection refused")))
        assertFalse(h.isAlreadyClosedError(RuntimeException("")))
    }

    @Test
    fun `isAlreadyClosedError returns false when message is null`() {
        val h = H2ShutdownHook()
        val ex =
            object : RuntimeException() {
                override val message: String? = null
            }
        assertFalse(h.isAlreadyClosedError(ex))
    }

    @Test
    fun `onApplicationEvent delegates to runShutdown`() {
        var calls = 0
        val h =
            hook(url = "jdbc:h2:mem:test").apply {
                executor =
                    H2ShutdownExecutor { _, _, _ -> calls++ }
            }
        h.onApplicationEvent(
            org.springframework.context.event.ContextClosedEvent(
                org.springframework.context.support
                    .StaticApplicationContext(),
            ),
        )
        assertEquals(1, calls)
    }

    private fun getField(
        target: Any,
        name: String,
    ): Any? {
        val f = H2ShutdownHook::class.java.getDeclaredField(name).apply { isAccessible = true }
        return f.get(target)
    }

    private inline fun withClearedProperties(block: () -> Unit) {
        val savedUrl = System.getProperty("spring.datasource.url")
        val savedUser = System.getProperty("spring.datasource.username")
        val savedPass = System.getProperty("spring.datasource.password")
        try {
            listOf(
                "spring.datasource.url",
                "spring.datasource.username",
                "spring.datasource.password",
            ).forEach { System.clearProperty(it) }
            block()
        } finally {
            if (savedUrl != null) System.setProperty("spring.datasource.url", savedUrl)
            if (savedUser != null) System.setProperty("spring.datasource.username", savedUser)
            if (savedPass != null) System.setProperty("spring.datasource.password", savedPass)
        }
    }
}
