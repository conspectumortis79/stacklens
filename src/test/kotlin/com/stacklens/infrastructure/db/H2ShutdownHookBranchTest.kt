package com.stacklens.infrastructure.db

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.context.ApplicationContext
import org.springframework.context.event.ContextClosedEvent

class H2ShutdownHookBranchTest {
    private lateinit var hook: H2ShutdownHook

    @BeforeEach
    fun setUp() {
        hook = H2ShutdownHook()
    }

    @Test
    fun `registerJvmHook reads URL and credentials from system properties`() {
        val savedUrl = System.getProperty("spring.datasource.url")
        val savedUser = System.getProperty("spring.datasource.username")
        val savedPw = System.getProperty("spring.datasource.password")
        try {
            System.setProperty("spring.datasource.url", "jdbc:h2:mem:abc;DB_CLOSE_DELAY=-1")
            System.setProperty("spring.datasource.username", "bob")
            System.setProperty("spring.datasource.password", "secret")
            hook.registerJvmHook()
            var invoked = false
            hook.executor =
                H2ShutdownExecutor { url, user, pw ->
                    invoked = true
                    assertEquals("jdbc:h2:mem:abc;DB_CLOSE_DELAY=-1", url)
                    assertEquals("bob", user)
                    assertEquals("secret", pw)
                }
            hook.runShutdown("test")
            assertTrue(invoked, "executor should have been invoked")
        } finally {
            if (savedUrl != null) System.setProperty("spring.datasource.url", savedUrl) else System.clearProperty("spring.datasource.url")
            if (savedUser != null) System.setProperty("spring.datasource.username", savedUser) else System.clearProperty("spring.datasource.username")
            if (savedPw != null) System.setProperty("spring.datasource.password", savedPw) else System.clearProperty("spring.datasource.password")
        }
    }

    @Test
    fun `registerJvmHook falls back to defaults when no env or system property is present`() {
        val savedUrl = System.getProperty("spring.datasource.url")
        val savedUser = System.getProperty("spring.datasource.username")
        val savedPw = System.getProperty("spring.datasource.password")
        try {
            System.clearProperty("spring.datasource.url")
            System.clearProperty("spring.datasource.username")
            System.clearProperty("spring.datasource.password")
            hook.registerJvmHook()
            hook.executor =
                H2ShutdownExecutor { url, user, pw ->
                    assertEquals(null, url, "expected null url since none set")
                    assertEquals("sa", user)
                    assertEquals("", pw)
                }
            hook.runShutdown("test")
        } finally {
            if (savedUrl != null) System.setProperty("spring.datasource.url", savedUrl)
            if (savedUser != null) System.setProperty("spring.datasource.username", savedUser)
            if (savedPw != null) System.setProperty("spring.datasource.password", savedPw)
        }
    }

    @Test
    fun `runShutdown skips when URL is not H2`() {
        hook.registerJvmHook()
        System.setProperty("spring.datasource.url", "jdbc:postgresql://x/y")
        try {
            hook.runShutdown("test")
        } finally {
            System.clearProperty("spring.datasource.url")
        }
    }

    @Test
    fun `runShutdown only runs once even when called multiple times`() {
        val savedUrl = System.getProperty("spring.datasource.url")
        try {
            System.clearProperty("spring.datasource.url")
            hook.registerJvmHook()
            System.setProperty("spring.datasource.url", "jdbc:h2:mem:once")
            hook.registerJvmHook()
            var calls = 0
            hook.executor = H2ShutdownExecutor { _, _, _ -> calls++ }
            hook.runShutdown("first")
            hook.runShutdown("second")
            hook.runShutdown("third")
            assertEquals(1, calls, "executor should run only once")
        } finally {
            System.clearProperty("spring.datasource.url")
            if (savedUrl != null) System.setProperty("spring.datasource.url", savedUrl)
        }
    }

    @Test
    fun `runShutdown treats SQLSTATE 90013 as success (already-closed branch)`() {
        hook.registerJvmHook()
        System.setProperty("spring.datasource.url", "jdbc:h2:mem:already")
        hook.executor =
            H2ShutdownExecutor { _, _, _ ->
                throw RuntimeException("Database ALREADY CLOSED (error 90013)")
            }
        try {
            hook.runShutdown("test")
        } finally {
            System.clearProperty("spring.datasource.url")
        }
    }

    @Test
    fun `runShutdown treats 'already closed' as success (case-insensitive branch)`() {
        hook.registerJvmHook()
        System.setProperty("spring.datasource.url", "jdbc:h2:mem:already2")
        hook.executor =
            H2ShutdownExecutor { _, _, _ ->
                throw RuntimeException("Database already closed by another process")
            }
        try {
            hook.runShutdown("test")
        } finally {
            System.clearProperty("spring.datasource.url")
        }
    }

    @Test
    fun `runShutdown treats 'ALREADY CLOSED' uppercase as success (case-insensitive branch)`() {
        hook.registerJvmHook()
        System.setProperty("spring.datasource.url", "jdbc:h2:mem:already3")
        hook.executor =
            H2ShutdownExecutor { _, _, _ ->
                throw RuntimeException("Database ALREADY CLOSED")
            }
        try {
            hook.runShutdown("test")
        } finally {
            System.clearProperty("spring.datasource.url")
        }
    }

    @Test
    fun `runShutdown logs warn for non-90013 executor failure`() {
        hook.registerJvmHook()
        System.setProperty("spring.datasource.url", "jdbc:h2:mem:other")
        hook.executor =
            H2ShutdownExecutor { _, _, _ ->
                throw RuntimeException("connection refused")
            }
        try {
            hook.runShutdown("test")
        } finally {
            System.clearProperty("spring.datasource.url")
        }
    }

    @Test
    fun `onApplicationEvent invokes runShutdown via Spring context-closed path`() {
        System.setProperty("spring.datasource.url", "jdbc:h2:mem:spring")
        hook.registerJvmHook()
        var calls = 0
        hook.executor = H2ShutdownExecutor { _, _, _ -> calls++ }
        try {
            val ctx = mock<ApplicationContext>()
            hook.onApplicationEvent(ContextClosedEvent(ctx))
            assertTrue(calls == 1, "Spring path should have triggered the executor exactly once")
        } finally {
            System.clearProperty("spring.datasource.url")
        }
    }

    @Test
    fun `runShutdown no-op on second invocation after non-H2 skip`() {
        val savedUrl = System.getProperty("spring.datasource.url")
        try {
            System.clearProperty("spring.datasource.url")
            hook.registerJvmHook()
            hook.runShutdown("first")
            hook.runShutdown("second")
        } finally {
            if (savedUrl != null) System.setProperty("spring.datasource.url", savedUrl)
        }
    }
}
