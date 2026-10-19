package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class ConfluenceToolCoverageTest {
    @Test
    fun `ConfluenceTool defaults are sensible`() {
        val t = ConfluenceTool(name = "n", confluencePageUrl = "https://x/page")
        assertEquals(0L, t.id)
        assertEquals("n", t.name)
        assertNull(t.description)
        assertEquals("https://x/page", t.confluencePageUrl)
        assertNull(t.confluenceConfigId)
        assertEquals("PENDING", t.status)
        assertNull(t.currentStep)
        assertNull(t.pageContentText)
        assertNull(t.generationPrompt)
        assertNull(t.generatedCode)
        assertNull(t.jarPath)
        assertNull(t.lastRunOutput)
        assertNull(t.lastRunAt)
        assertNull(t.errorMessage)
        assertNotNull(t.createdAt)
        assertNull(t.completedAt)
        assertFalse(t.hiddenFromHistory)
        assertNull(t.icon)
    }

    @Test
    fun `ConfluenceTool stores provided values verbatim`() {
        val now = LocalDateTime.of(2025, 1, 1, 0, 0)
        val done = LocalDateTime.of(2025, 1, 1, 1, 0)
        val lastRun = LocalDateTime.of(2025, 1, 2, 0, 0)
        val t =
            ConfluenceTool(
                id = 7L,
                name = "n",
                description = "d",
                confluencePageUrl = "u",
                confluenceConfigId = 3L,
                status = "READY",
                currentStep = "step",
                pageContentText = "page",
                generationPrompt = "prompt",
                generatedCode = "fun main() = 0",
                jarPath = "/tmp/a.jar",
                lastRunOutput = "out",
                lastRunAt = lastRun,
                errorMessage = "err",
                createdAt = now,
                completedAt = done,
                hiddenFromHistory = true,
                icon = "bi-database",
            )
        assertEquals(7L, t.id)
        assertEquals("d", t.description)
        assertEquals(3L, t.confluenceConfigId)
        assertEquals("READY", t.status)
        assertEquals("step", t.currentStep)
        assertEquals("page", t.pageContentText)
        assertEquals("prompt", t.generationPrompt)
        assertEquals("fun main() = 0", t.generatedCode)
        assertEquals("/tmp/a.jar", t.jarPath)
        assertEquals("out", t.lastRunOutput)
        assertEquals(lastRun, t.lastRunAt)
        assertEquals("err", t.errorMessage)
        assertEquals(now, t.createdAt)
        assertEquals(done, t.completedAt)
        assertEquals("bi-database", t.icon)
    }
}
