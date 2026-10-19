package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class JiraConfigGetCreatedAtTest {
    @Test
    fun `JiraConfig exposes createdAt through its Kotlin getter`() {
        val ts = LocalDateTime.of(2024, 1, 1, 12, 0, 0)
        val c =
            JiraConfig(
                name = "j",
                baseUrl = "https://j",
                apiToken = "t",
                createdAt = ts,
            )
        assertNotNull(c.createdAt)
        assertEquals(ts, c.createdAt)
    }
}
