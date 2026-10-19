package com.stacklens.application.service

import com.stacklens.application.port.out.prompt.PromptGroup
import com.stacklens.application.port.out.prompt.PromptLoaderPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class PromptManagementServiceTest {
    @Mock
    lateinit var promptLoader: PromptLoaderPort

    private val service by lazy { PromptManagementService(promptLoader) }

    @Test
    fun `listKeys delegates to port`() {
        whenever(promptLoader.listKeys("de", "jira")).thenReturn(listOf("jira.key1", "jira.key2"))
        val result = service.listKeys("de", "jira")
        assertEquals(listOf("jira.key1", "jira.key2"), result)
    }

    @Test
    fun `listGroups maps port groups to PromptGroupSummary`() {
        whenever(promptLoader.listGroups("de", "jira")).thenReturn(
            listOf(PromptGroup("Jira Analysis", "jira.analyse"), PromptGroup("Jira Review", "jira.review")),
        )
        val result = service.listGroups("de", "jira")
        assertEquals(2, result.size)
        assertEquals("Jira Analysis", result[0].label)
        assertEquals("jira.analyse", result[0].mainKey)
    }

    @Test
    fun `getPrompt returns custom content when custom differs from default`() {
        whenever(promptLoader.getCustom("jira.analyse")).thenReturn("My custom prompt")
        whenever(promptLoader.getDefault("de", "jira.analyse")).thenReturn("Default prompt")
        val result = service.getPrompt("jira.analyse", "de")
        assertEquals("My custom prompt", result.content)
        assertTrue(result.isCustom)
        assertEquals("Default prompt", result.defaultContent)
    }

    @Test
    fun `getPrompt returns default content when no custom exists`() {
        whenever(promptLoader.getCustom("jira.analyse")).thenReturn(null)
        whenever(promptLoader.getDefault("de", "jira.analyse")).thenReturn("Default prompt")
        val result = service.getPrompt("jira.analyse", "de")
        assertEquals("Default prompt", result.content)
        assertFalse(result.isCustom)
    }

    @Test
    fun `getPrompt returns default when custom matches default`() {
        whenever(promptLoader.getCustom("jira.analyse")).thenReturn("Same content")
        whenever(promptLoader.getDefault("de", "jira.analyse")).thenReturn("Same content")
        val result = service.getPrompt("jira.analyse", "de")
        assertFalse(result.isCustom)
        assertEquals("Same content", result.content)
    }

    @Test
    fun `getPrompt returns default when custom differs only by surrounding whitespace`() {
        whenever(promptLoader.getCustom("jira.analyse")).thenReturn("  Same content  ")
        whenever(promptLoader.getDefault("de", "jira.analyse")).thenReturn("Same content")

        val result = service.getPrompt("jira.analyse", "de")

        assertFalse(result.isCustom)
        assertEquals("Same content", result.content)
    }

    @Test
    fun `savePrompt deletes custom when content matches default`() {
        whenever(promptLoader.getDefault("de", "jira.analyse")).thenReturn("Default content")
        service.savePrompt("jira.analyse", "Default content")
        verify(promptLoader).deleteCustom("jira.analyse")
    }

    @Test
    fun `savePrompt saves custom when content differs from default`() {
        whenever(promptLoader.getDefault("de", "jira.analyse")).thenReturn("Default content")
        service.savePrompt("jira.analyse", "My custom content")
        verify(promptLoader).saveCustom("jira.analyse", "My custom content")
    }

    @Test
    fun `savePrompt deletes custom when content matches default after trimming`() {
        whenever(promptLoader.getDefault("de", "jira.analyse")).thenReturn("Default content")

        service.savePrompt("jira.analyse", "  Default content   ")

        verify(promptLoader).deleteCustom("jira.analyse")
    }

    @Test
    fun `deletePrompt calls deleteCustom and returns default content`() {
        whenever(promptLoader.getDefault("de", "jira.analyse")).thenReturn("Default content")
        val result = service.deletePrompt("jira.analyse", "de")
        verify(promptLoader).deleteCustom("jira.analyse")
        assertEquals("Default content", result)
    }
}
