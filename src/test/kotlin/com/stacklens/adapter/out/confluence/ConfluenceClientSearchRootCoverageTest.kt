package com.stacklens.adapter.out.confluence

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConfluenceClientSearchRootCoverageTest {
    private val client = ConfluenceClient()

    @Test
    fun `an exact root url matches the page`() {
        assertTrue(client.matchesAnySearchRoot(listOf("https://wiki.example.com/space"), "https://wiki.example.com/space"))
    }

    @Test
    fun `a page below the root url matches`() {
        assertTrue(client.matchesAnySearchRoot(listOf("https://wiki.example.com/space"), "https://wiki.example.com/space/child"))
    }

    @Test
    fun `a page outside every root does not match`() {
        assertFalse(client.matchesAnySearchRoot(listOf("https://wiki.example.com/other"), "https://wiki.example.com/space/child"))
    }

    @Test
    fun `without any root every page matches`() {
        assertTrue(client.matchesAnySearchRoot(emptyList(), "https://wiki.example.com/space/child"))
    }
}
