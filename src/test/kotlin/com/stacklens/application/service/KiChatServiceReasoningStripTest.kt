package com.stacklens.application.service

import com.stacklens.adapter.out.image.ImageInlinePreparer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KiChatServiceReasoningStripTest {
    private val service =
        KiChatService(
            copilot = org.mockito.kotlin.mock(),
            i18n = org.mockito.kotlin.mock(),
            aiUsageContext = org.mockito.kotlin.mock(),
            knowledgeRetrieval = org.mockito.kotlin.mock(),
            promptBuilder = KiChatServiceTestSupport.buildPromptLoader(org.mockito.kotlin.mock(), org.mockito.kotlin.mock()),
            mcpPreflight = KiChatServiceTestSupport.buildNoopPreflight(),
            attachmentPreparer =
                KiChatAttachmentPreparer(
                    ImageInlinePreparer(
                        com.stacklens.adapter.out.image
                            .DefaultInlineImageEncoder(),
                    ),
                ),
        )

    @Test
    fun `strips MiniMax m3 mm_think blocks with leading zero-width space`() {
        val input = "<mm:think>reasoning here</mm:think>visible answer"
        assertEquals("visible answer", service.stripReasoningBlocks(input))
    }

    @Test
    fun `strips plain think blocks without zero-width space`() {
        val input = "<think>reasoning</think>Antwort"
        assertEquals("Antwort", service.stripReasoningBlocks(input))
    }

    @Test
    fun `strips OpenAI o1 thinking tags`() {
        val input = "<thinking>chain of thought</thinking>final"
        assertEquals("final", service.stripReasoningBlocks(input))
    }

    @Test
    fun `strips reasoning tags from some providers`() {
        val input = "<reasoning>step 1</reasoning>step 2<reflection>meta</reflection>done"
        assertEquals("step 2done", service.stripReasoningBlocks(input))
    }

    @Test
    fun `preserves text when no reasoning block is present`() {
        val input = "Just a normal answer without any thinking."
        assertEquals(input, service.stripReasoningBlocks(input))
    }

    @Test
    fun `handles multiple thinking blocks in one response`() {
        val input = "<think>A</think>mid<thinking>B</thinking>end"
        assertEquals("midend", service.stripReasoningBlocks(input))
    }

    @Test
    fun `handles multiline reasoning content with newlines`() {
        val input = "<mm:think>line1\nline2\nline3</mm:think>final answer"
        assertEquals("final answer", service.stripReasoningBlocks(input))
    }

    @Test
    fun `passes through empty and null-equivalent strings`() {
        assertEquals("", service.stripReasoningBlocks(""))
        assertEquals("normal", service.stripReasoningBlocks("normal"))
    }

    @Test
    fun `does not strip when only part of the tag is present e g broken tag`() {
        val input = "<thinking>the model forgot to close the tag"
        assertEquals(input, service.stripReasoningBlocks(input))
    }

    @Test
    fun `the exact user-reported format yields only the answer`() {
        val exactRepro =
            "<mm:think>The user is asking about Nvidia Reflex, AMD Anti-Lag 2, and Intel XeLL - " +
                "these are low-latency technologies for gaming. This is not directly related to StackLens, " +
                "but the user is in the \"Sprint-Analyse\" tab and just happens to ask this question. " +
                "I should answer it concisely in German as per the instructions.\n" +
                "Let me provide a clear comparison of these three technologies.</mm:think>" +
                "Vergleich der drei Latenz-Technologien:\n- Nvidia Reflex …"
        assertEquals(
            "Vergleich der drei Latenz-Technologien:\n- Nvidia Reflex …",
            service.stripReasoningBlocks(exactRepro),
        )
    }
}
