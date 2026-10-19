package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JiraConfigTest {
    @Test
    fun `default cloudMode is true`() {
        val config =
            JiraConfig(
                name = "jira",
                baseUrl = "https://jira.example.com",
                apiToken = "tok",
            )
        assertEquals(true, config.cloudMode)
    }

    @Test
    fun `default active is true`() {
        val config =
            JiraConfig(
                name = "jira",
                baseUrl = "https://jira.example.com",
                apiToken = "tok",
            )
        assertEquals(true, config.active)
    }

    @Test
    fun `default projectKey is empty string`() {
        val config =
            JiraConfig(
                name = "jira",
                baseUrl = "https://jira.example.com",
                apiToken = "tok",
            )
        assertEquals("", config.projectKey)
    }
}
