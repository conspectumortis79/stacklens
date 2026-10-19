package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocalRepoConfigTest {
    @Test
    fun `default constructor populates sensible defaults`() {
        val c = LocalRepoConfig(name = "n", path = "/p")
        assertEquals(0L, c.id)
        assertEquals("n", c.name)
        assertEquals("/p", c.path)
        assertTrue(c.active)
        assertEquals("REPO_ANALYSIS", c.scope)
    }

    @Test
    fun `named constructor populates all fields`() {
        val c = LocalRepoConfig(id = 1L, name = "n", path = "/p", active = false, scope = "CODE_ANALYSIS")
        assertEquals(1L, c.id)
        assertEquals(false, c.active)
        assertEquals("CODE_ANALYSIS", c.scope)
    }
}

class GrafanaConfigTest {
    @Test
    fun `default constructor populates sensible defaults`() {
        val c = GrafanaConfig(name = "n", baseUrl = "u", username = "u", password = "p")
        assertEquals(0L, c.id)
        assertEquals("n", c.name)
        assertEquals("u", c.baseUrl)
        assertEquals("u", c.username)
        assertEquals("p", c.password)
        assertEquals("", c.apiToken)
        assertEquals(1L, c.orgId)
        assertTrue(c.scanPanelQueries)
        assertTrue(c.active)
    }

    @Test
    fun `named constructor populates all fields`() {
        val c =
            GrafanaConfig(
                id = 1L,
                name = "n",
                baseUrl = "u",
                username = "u",
                password = "p",
                apiToken = "tok",
                orgId = 5L,
                scanPanelQueries = false,
                active = false,
            )
        assertEquals(1L, c.id)
        assertEquals("tok", c.apiToken)
        assertEquals(5L, c.orgId)
        assertEquals(false, c.scanPanelQueries)
        assertEquals(false, c.active)
    }

    @Test
    fun `is-a ConnectionEntity so it can be passed to health probes`() {
        val c: ConnectionEntity = GrafanaConfig(name = "n", baseUrl = "u", username = "u", password = "p")
        assertEquals("n", c.name)
        assertNull(c.lastErrorMessage)
    }
}
