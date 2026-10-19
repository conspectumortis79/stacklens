package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GithubRepoConfigTest {
    @Test
    fun `default constructor populates sensible defaults`() {
        val c = GithubRepoConfig(name = "n", owner = "o", repo = "r")
        assertEquals(0L, c.id)
        assertEquals("n", c.name)
        assertEquals("o", c.owner)
        assertEquals("r", c.repo)
        assertNull(c.branch)
        assertNull(c.accessToken)
        assertNull(c.apiBase)
        assertTrue(c.active)
        assertEquals(0, c.consecutiveFailures)
        assertNull(c.lastErrorAt)
        assertNull(c.lastErrorMessage)
    }

    @Test
    fun `named constructor populates all fields`() {
        val c =
            GithubRepoConfig(
                id = 1L,
                name = "n",
                owner = "o",
                repo = "r",
                branch = "main",
                accessToken = "tok",
                apiBase = "https://api.github.com",
                active = false,
                consecutiveFailures = 3,
                lastErrorMessage = "boom",
            )
        assertEquals(1L, c.id)
        assertEquals("main", c.branch)
        assertEquals("tok", c.accessToken)
        assertEquals("https://api.github.com", c.apiBase)
        assertEquals(false, c.active)
        assertEquals(3, c.consecutiveFailures)
        assertEquals("boom", c.lastErrorMessage)
    }

    @Test
    fun `is-a ConnectionEntity so it can be passed to health probes`() {
        val c: ConnectionEntity = GithubRepoConfig(name = "n", owner = "o", repo = "r")
        assertEquals("n", c.name)
        assertTrue(c.active)
    }
}
