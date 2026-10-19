package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.application.port.inbound.prompt.PromptContent
import com.stacklens.application.port.inbound.prompt.PromptFileReadFailure
import com.stacklens.application.port.inbound.prompt.PromptFileReadResult
import com.stacklens.application.port.inbound.prompt.PromptFileReadUseCase
import com.stacklens.application.port.inbound.prompt.PromptGroupSummary
import com.stacklens.application.port.inbound.prompt.PromptManagementUseCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus

class PromptApiControllerTest {
    private val promptManager = mock<PromptManagementUseCase>()
    private val i18n = mock<I18nUseCase>()

    private val promptFiles: PromptFileReadUseCase = mock()

    private fun newController(): PromptApiController = PromptApiController(promptManager, promptFiles, i18n)

    @Test
    fun `listPromptKeys forwards the lang and prefix to the use case and returns the result`() {
        whenever(promptManager.listKeys("de", "x.")).thenReturn(listOf("x.a", "x.b"))
        val response = newController().listPromptKeys("x.", "de")
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(listOf("x.a", "x.b"), response.body)
    }

    @Test
    fun `listPromptGroups returns 200 with label and mainKey for every group`() {
        whenever(promptManager.listGroups("de", "y.")).thenReturn(
            listOf(
                PromptGroupSummary(label = "Group 1", mainKey = "y.1"),
                PromptGroupSummary(label = "Group 2", mainKey = "y.2"),
            ),
        )
        val response = newController().listPromptGroups("y.", "de")
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(2, response.body!!.size)
        assertEquals("Group 1", response.body!![0]["label"])
        assertEquals("y.1", response.body!![0]["mainKey"])
        assertEquals("Group 2", response.body!![1]["label"])
        assertEquals("y.2", response.body!![1]["mainKey"])
    }

    @Test
    fun `getPrompt returns 200 with the content payload`() {
        whenever(promptManager.getPrompt("k", "de"))
            .thenReturn(PromptContent(key = "k", content = "user", isCustom = true, defaultContent = "default"))
        val response = newController().getPrompt("k", "de")
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("k", response.body!!["key"])
        assertEquals("user", response.body!!["content"])
        assertEquals(true, response.body!!["isCustom"])
        assertEquals("default", response.body!!["defaultContent"])
    }

    @Test
    fun `savePrompt returns 400 with the localized missing-content key when the body has no content field`() {
        whenever(i18n.t(any<String>())).thenAnswer { "translated" }
        val response = newController().savePrompt("k", emptyMap())
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("translated", response.body!!["error"])
    }

    @Test
    fun `savePrompt returns 400 with the localized blank-content key when the body content is blank`() {
        whenever(i18n.t(any<String>())).thenAnswer { "blank msg" }
        val response = newController().savePrompt("k", mapOf("content" to "   "))
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("blank msg", response.body!!["error"])
    }

    @Test
    fun `savePrompt returns 200 ok when the content is non-blank`() {
        whenever(i18n.t(any<String>())).thenAnswer { "ignored" }
        val response = newController().savePrompt("k", mapOf("content" to "hello"))
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(true, response.body!!["ok"])
        assertEquals(true, response.body!!["isCustom"])
        verify(promptManager).savePrompt("k", "hello")
    }

    @Test
    fun `resetPrompt returns 200 with the default content and isCustom=false`() {
        whenever(promptManager.deletePrompt("k", "de")).thenReturn("default-content")
        val response = newController().resetPrompt("k", "de")
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(true, response.body!!["ok"])
        assertEquals("default-content", response.body!!["content"])
        assertEquals(false, response.body!!["isCustom"])
    }

    @Test
    fun `readPromptFile maps every use case outcome to its http answer`() {
        whenever(promptFiles.readPromptFile("inside")).thenReturn(PromptFileReadResult.Success("hello"))
        assertEquals(mapOf("content" to "hello"), newController().readPromptFile("inside").body)

        whenever(promptFiles.readPromptFile("denied")).thenReturn(PromptFileReadResult.Failed(PromptFileReadFailure.ACCESS_DENIED))
        assertEquals(HttpStatus.BAD_REQUEST, newController().readPromptFile("denied").statusCode)

        whenever(promptFiles.readPromptFile("gone")).thenReturn(PromptFileReadResult.Failed(PromptFileReadFailure.NOT_FOUND))
        assertEquals(HttpStatus.BAD_REQUEST, newController().readPromptFile("gone").statusCode)

        whenever(promptFiles.readPromptFile("huge")).thenReturn(PromptFileReadResult.Failed(PromptFileReadFailure.TOO_LARGE))
        assertEquals(HttpStatus.BAD_REQUEST, newController().readPromptFile("huge").statusCode)

        whenever(promptFiles.readPromptFile("broken")).thenReturn(PromptFileReadResult.Failed(PromptFileReadFailure.READ_FAILED, "disk error"))
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, newController().readPromptFile("broken").statusCode)
    }

    @Test
    fun `readPromptFile interpolates the failure message into the translated error`() {
        whenever(i18n.t(eq("prompt.be_read_error"), any())).thenReturn("READ ERROR boom")
        whenever(promptFiles.readPromptFile("broken")).thenReturn(PromptFileReadResult.Failed(PromptFileReadFailure.READ_FAILED, "boom"))

        val response = newController().readPromptFile("broken")

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals("READ ERROR boom", response.body!!["error"])
    }
}
