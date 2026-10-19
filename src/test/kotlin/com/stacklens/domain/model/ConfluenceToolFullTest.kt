package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ConfluenceToolFullTest {
    @Test
    fun `ConfluenceTool default values are correct`() {
        val t =
            ConfluenceTool(
                name = "My Tool",
                confluencePageUrl = "https://confluence.example.com/page/1",
            )
        assertEquals(0L, t.id)
        assertEquals("My Tool", t.name)
        assertNull(t.description)
        assertEquals("https://confluence.example.com/page/1", t.confluencePageUrl)
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
    fun `ConfluenceTool supports mutating all var fields`() {
        val t =
            ConfluenceTool(
                name = "T",
                confluencePageUrl = "u",
            )
        t.confluencePageUrl = "u2"
        assertEquals("u2", t.confluencePageUrl)
        t.description = "desc"
        t.confluenceConfigId = 5L
        t.status = "RUNNING"
        t.currentStep = "step"
        t.pageContentText = "content"
        t.generationPrompt = "p"
        t.generatedCode = "code"
        t.jarPath = "/tmp/x.jar"
        t.lastRunOutput = "out"
        t.lastRunAt = java.time.LocalDateTime.now()
        t.errorMessage = "err"
        t.completedAt = java.time.LocalDateTime.now()
        t.hiddenFromHistory = true
        t.icon = "bi-database"
        assertEquals("desc", t.description)
        assertEquals(5L, t.confluenceConfigId)
        assertEquals("RUNNING", t.status)
        assertEquals("step", t.currentStep)
        assertEquals("content", t.pageContentText)
        assertEquals("p", t.generationPrompt)
        assertEquals("code", t.generatedCode)
        assertEquals("/tmp/x.jar", t.jarPath)
        assertEquals("out", t.lastRunOutput)
        assertNotNull(t.lastRunAt)
        assertEquals("err", t.errorMessage)
        assertNotNull(t.completedAt)
        assertEquals(true, t.hiddenFromHistory)
        assertEquals("bi-database", t.icon)
    }

    @Test
    fun `ConfluenceTool full construction with all fields`() {
        val now = java.time.LocalDateTime.now()
        val t =
            ConfluenceTool(
                id = 11L,
                name = "Full Tool",
                description = "d",
                confluencePageUrl = "https://confluence.example.com/page/2",
                confluenceConfigId = 7L,
                status = "READY",
                currentStep = "done",
                pageContentText = "text",
                generationPrompt = "prompt",
                generatedCode = "code",
                jarPath = "/jar",
                lastRunOutput = "ok",
                lastRunAt = now,
                errorMessage = null,
                completedAt = now,
                hiddenFromHistory = true,
                icon = "bi-tools",
            )
        assertEquals(11L, t.id)
        assertEquals("Full Tool", t.name)
        assertEquals("d", t.description)
        assertEquals(7L, t.confluenceConfigId)
        assertEquals("READY", t.status)
        assertEquals("done", t.currentStep)
        assertEquals("text", t.pageContentText)
        assertEquals("prompt", t.generationPrompt)
        assertEquals("code", t.generatedCode)
        assertEquals("/jar", t.jarPath)
        assertEquals("ok", t.lastRunOutput)
        assertEquals(now, t.lastRunAt)
        assertNull(t.errorMessage)
        assertEquals(now, t.completedAt)
        assertEquals(true, t.hiddenFromHistory)
        assertEquals("bi-tools", t.icon)
    }
}
