package com.stacklens.infrastructure.db

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class H2ShutdownHookCaptureAndPredicateTest {
    @Test
    fun `captureConfig uses env-var for URL when set`() {
        val hook = H2ShutdownHook()
        hook.captureConfig(envLookup = { key ->
            if (key == "SPRING_DATASOURCE_URL") "jdbc:h2:mem:env-only" else null
        })
        var captured: String? = null
        hook.executor = H2ShutdownExecutor { url, _, _ -> captured = url }
        hook.runShutdown("test")
        assertEquals("jdbc:h2:mem:env-only", captured)
    }

    @Test
    fun `captureConfig uses env-var for USERNAME when set`() {
        val hook = H2ShutdownHook()
        hook.captureConfig(envLookup = { key ->
            when (key) {
                "SPRING_DATASOURCE_URL" -> "jdbc:h2:mem:env"
                "SPRING_DATASOURCE_USERNAME" -> "env-user"
                else -> null
            }
        })
        var capturedUser: String? = null
        hook.executor = H2ShutdownExecutor { _, u, _ -> capturedUser = u }
        hook.runShutdown("test")
        assertEquals("env-user", capturedUser)
    }

    @Test
    fun `captureConfig uses env-var for PASSWORD when set`() {
        val hook = H2ShutdownHook()
        hook.captureConfig(envLookup = { key ->
            when (key) {
                "SPRING_DATASOURCE_URL" -> "jdbc:h2:mem:env"
                "SPRING_DATASOURCE_PASSWORD" -> "env-pw"
                else -> null
            }
        })
        var capturedPw: String? = null
        hook.executor = H2ShutdownExecutor { _, _, p -> capturedPw = p }
        hook.runShutdown("test")
        assertEquals("env-pw", capturedPw)
    }

    @Test
    fun `captureConfig falls back to system properties when env vars are absent`() {
        val savedUrl = System.getProperty("spring.datasource.url")
        val savedUser = System.getProperty("spring.datasource.username")
        val savedPw = System.getProperty("spring.datasource.password")
        try {
            System.setProperty("spring.datasource.url", "jdbc:h2:mem:sys")
            System.setProperty("spring.datasource.username", "sys-user")
            System.setProperty("spring.datasource.password", "sys-pw")
            val hook = H2ShutdownHook()
            hook.captureConfig(envLookup = { _ -> null })
            var u: String? = null
            var user: String? = null
            var pw: String? = null
            hook.executor =
                H2ShutdownExecutor { url, un, p ->
                    u = url
                    user = un
                    pw = p
                }
            hook.runShutdown("test")
            assertEquals("jdbc:h2:mem:sys", u)
            assertEquals("sys-user", user)
            assertEquals("sys-pw", pw)
        } finally {
            if (savedUrl != null) System.setProperty("spring.datasource.url", savedUrl) else System.clearProperty("spring.datasource.url")
            if (savedUser != null) System.setProperty("spring.datasource.username", savedUser) else System.clearProperty("spring.datasource.username")
            if (savedPw != null) System.setProperty("spring.datasource.password", savedPw) else System.clearProperty("spring.datasource.password")
        }
    }

    @Test
    fun `isAlreadyClosedError returns true for 90013 message`() {
        val hook = H2ShutdownHook()
        assertTrue(hook.isAlreadyClosedError(RuntimeException("Database ALREADY CLOSED (error 90013)")))
    }

    @Test
    fun `isAlreadyClosedError returns true for already closed message`() {
        val hook = H2ShutdownHook()
        assertTrue(hook.isAlreadyClosedError(RuntimeException("Database already closed")))
    }

    @Test
    fun `isAlreadyClosedError returns false for unrelated message`() {
        val hook = H2ShutdownHook()
        assertFalse(hook.isAlreadyClosedError(RuntimeException("connection refused")))
    }

    @Test
    fun `isAlreadyClosedError returns false for null message`() {
        val hook = H2ShutdownHook()
        val ex =
            object : RuntimeException() {
                override val message: String? = null
            }
        assertFalse(hook.isAlreadyClosedError(ex))
    }
}
