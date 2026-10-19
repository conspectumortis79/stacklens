package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ConfluenceToolTest {
    @Test
    fun `default constructor populates sensible defaults`() {
        val t = ConfluenceTool(name = "n", confluencePageUrl = "u")
        assertEquals(0L, t.id)
        assertEquals("n", t.name)
        assertNull(t.description)
        assertEquals("u", t.confluencePageUrl)
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
        assertNull(t.completedAt)
        assertFalse(t.hiddenFromHistory)
        assertNull(t.icon)
    }

    @Test
    fun `named constructor populates all fields`() {
        val t =
            ConfluenceTool(
                id = 1L,
                name = "n",
                description = "d",
                confluencePageUrl = "u",
                confluenceConfigId = 2L,
                status = "READY",
                currentStep = "step",
                pageContentText = "text",
                generationPrompt = "prompt",
                generatedCode = "code",
                jarPath = "/path/to.jar",
                lastRunOutput = "out",
                errorMessage = "err",
                hiddenFromHistory = true,
                icon = "bi-database",
            )
        assertEquals(1L, t.id)
        assertEquals("d", t.description)
        assertEquals(2L, t.confluenceConfigId)
        assertEquals("READY", t.status)
        assertEquals("step", t.currentStep)
        assertEquals("text", t.pageContentText)
        assertEquals("prompt", t.generationPrompt)
        assertEquals("code", t.generatedCode)
        assertEquals("/path/to.jar", t.jarPath)
        assertEquals("out", t.lastRunOutput)
        assertEquals("err", t.errorMessage)
        assertEquals(true, t.hiddenFromHistory)
        assertEquals("bi-database", t.icon)
    }
}
