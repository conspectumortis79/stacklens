package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.application.port.inbound.prompt.AgentPromptContent
import com.stacklens.application.port.inbound.prompt.AgentPromptSaveOutcome
import com.stacklens.application.port.inbound.prompt.AgentPromptSummary
import com.stacklens.application.port.inbound.prompt.AgentPromptUseCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AgentFileApiControllerTest {
    private val agentPrompts: AgentPromptUseCase = mock()
    private val i18n: I18nUseCase = mock()
    private val controller = AgentFileApiController(agentPrompts, i18n)

    private val content =
        AgentPromptContent(
            name = "nora",
            content = "custom body",
            isCustom = true,
            defaultContent = "default body",
        )

    @Test
    fun `listAgents renders name and label pairs`() {
        whenever(agentPrompts.listAgents()).thenReturn(listOf(AgentPromptSummary("nora", "Nora (Analyse)")))

        assertEquals(listOf(mapOf("name" to "nora", "label" to "Nora (Analyse)")), controller.listAgents())
    }

    @Test
    fun `getAgent exposes the use case payload`() {
        whenever(agentPrompts.getAgent("nora")).thenReturn(content)

        val response = controller.getAgent("nora")

        assertEquals(200, response.statusCode.value())
        assertEquals(
            mapOf(
                "name" to "nora",
                "content" to "custom body",
                "isCustom" to true,
                "defaultContent" to "default body",
            ),
            response.body,
        )
    }

    @Test
    fun `an unknown agent answers with 404`() {
        whenever(agentPrompts.getAgent("ghost")).thenReturn(null)

        assertEquals(404, controller.getAgent("ghost").statusCode.value())
    }

    @Test
    fun `saving without a content field answers with the translated error`() {
        whenever(i18n.t(any(), *emptyArray())).thenReturn("content fehlt")

        val response = controller.saveAgent("nora", emptyMap())

        assertEquals(400, response.statusCode.value())
        assertEquals("content fehlt", response.body?.get("error"))
        verify(agentPrompts, never()).saveAgent(any(), any())
    }

    @Test
    fun `the save outcome of the use case is mapped to the http status`() {
        whenever(agentPrompts.saveAgent(eq("nora"), eq("body"))).thenReturn(AgentPromptSaveOutcome.SAVED)
        assertEquals(mapOf("ok" to true, "isCustom" to true), controller.saveAgent("nora", mapOf("content" to "body")).body)

        whenever(agentPrompts.saveAgent(eq("nora"), eq("   "))).thenReturn(AgentPromptSaveOutcome.BLANK_CONTENT)
        assertEquals(400, controller.saveAgent("nora", mapOf("content" to "   ")).statusCode.value())

        whenever(agentPrompts.saveAgent(eq("ghost"), any())).thenReturn(AgentPromptSaveOutcome.UNKNOWN_AGENT)
        assertEquals(404, controller.saveAgent("ghost", mapOf("content" to "body")).statusCode.value())
    }

    @Test
    fun `reset answers with the restored default content`() {
        whenever(agentPrompts.resetAgent("nora")).thenReturn(content.copy(content = "default body", isCustom = false))

        val response = controller.resetAgent("nora")

        assertEquals(200, response.statusCode.value())
        assertEquals(mapOf("ok" to true, "content" to "default body", "isCustom" to false), response.body)
    }

    @Test
    fun `resetting an unknown agent answers with 404`() {
        whenever(agentPrompts.resetAgent("ghost")).thenReturn(null)

        assertEquals(404, controller.resetAgent("ghost").statusCode.value())
    }

    @Test
    fun `the controller keeps no state of its own`() {
        whenever(agentPrompts.getAgent("nora")).thenReturn(content)

        assertFalse(controller.getAgent("nora").body?.get("isCustom") == false)
    }
}
