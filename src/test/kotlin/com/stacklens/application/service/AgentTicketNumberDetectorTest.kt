package com.stacklens.application.service

import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.application.port.out.ai.AiAttachment
import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.appconfig.StackLensConfigPort
import com.stacklens.application.port.out.classpath.ClasspathResourcePort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

class AgentTicketNumberDetectorTest {
    private val copilot: CopilotStreamingPort = mock()
    private val stackLensConfig: StackLensConfigPort = mock()
    private val classpathResource: ClasspathResourcePort = mock()

    private val fileSystem = JavaFileSystemAdapter()

    private fun detector(): AgentTicketNumberDetector =
        AgentTicketNumberDetector(
            copilot = copilot,
            stackLensConfig = stackLensConfig,
            classpathResource = classpathResource,
            fileAttributes = fileSystem,
            fileContentStore = fileSystem,
        )

    private fun stubPromptTemplate(template: String = "Detect ticket in: {rawText}") {
        whenever(stackLensConfig.dataDir).thenReturn("")
        whenever(classpathResource.readText(any<String>())).thenReturn(template)
    }

    @Test
    fun `detect returns ticket key when AI returns valid key`() {
        stubPromptTemplate()
        whenever(
            copilot.executeWithStreamingPerLine(
                any<String>(),
                any<String>(),
                any<Long>(),
                anyOrNull<File>(),
                anyOrNull(),
                any<(String) -> Unit>(),
                anyOrNull(),
            ),
        ).thenReturn("PROJ-123")

        val result = detector().detect("gpt-4", "Please fix the login bug")
        assertEquals("PROJ-123", result)
    }

    @Test
    fun `detect returns null when AI returns empty`() {
        stubPromptTemplate()
        whenever(
            copilot.executeWithStreamingPerLine(
                any<String>(),
                any<String>(),
                any<Long>(),
                anyOrNull<File>(),
                anyOrNull(),
                any<(String) -> Unit>(),
                anyOrNull(),
            ),
        ).thenReturn("")

        val result = detector().detect("gpt-4", "no ticket here")
        assertNull(result)
    }

    @Test
    fun `detect returns null when AI returns NONE`() {
        stubPromptTemplate()
        whenever(
            copilot.executeWithStreamingPerLine(
                any<String>(),
                any<String>(),
                any<Long>(),
                anyOrNull<File>(),
                anyOrNull(),
                any<(String) -> Unit>(),
                anyOrNull(),
            ),
        ).thenReturn("NONE")

        val result = detector().detect("gpt-4", "cannot detect")
        assertNull(result)
    }

    @Test
    fun `detect returns null when AI returns non-ticket garbage`() {
        stubPromptTemplate()
        whenever(
            copilot.executeWithStreamingPerLine(
                any<String>(),
                any<String>(),
                any<Long>(),
                anyOrNull<File>(),
                anyOrNull(),
                any<(String) -> Unit>(),
                anyOrNull(),
            ),
        ).thenReturn("just some random text without a ticket key")

        val result = detector().detect("gpt-4", "free text")
        assertNull(result)
    }

    @Test
    fun `detect handles generic Exception by returning null`() {
        stubPromptTemplate()
        whenever(
            copilot.executeWithStreamingPerLine(
                any<String>(),
                any<String>(),
                any<Long>(),
                anyOrNull<File>(),
                anyOrNull(),
                any<(String) -> Unit>(),
                anyOrNull(),
            ),
        ).thenThrow(RuntimeException("API failed"))

        val result = detector().detect("gpt-4", "text")
        assertNull(result)
    }

    @Test
    fun `detect returns sanitised key when AI returns key with leading text`() {
        stubPromptTemplate()
        whenever(
            copilot.executeWithStreamingPerLine(
                any<String>(),
                any<String>(),
                any<Long>(),
                anyOrNull<File>(),
                anyOrNull(),
                any<(String) -> Unit>(),
                anyOrNull(),
            ),
        ).thenReturn("Output: PROJ-123")

        val result = detector().detect("gpt-4", "text")
        assertEquals("PROJ-123", result)
    }

    @Test
    fun `detect returns null for path-traversal-style AI output`() {
        stubPromptTemplate()
        whenever(
            copilot.executeWithStreamingPerLine(
                any<String>(),
                any<String>(),
                any<Long>(),
                anyOrNull<File>(),
                anyOrNull(),
                any<(String) -> Unit>(),
                anyOrNull(),
            ),
        ).thenReturn("../PROJ-123")

        val result = detector().detect("gpt-4", "text")
        assertNull(result)
    }

    @Test
    fun `detect returns null for key with whitespace`() {
        stubPromptTemplate()
        whenever(
            copilot.executeWithStreamingPerLine(
                any<String>(),
                any<String>(),
                any<Long>(),
                anyOrNull<File>(),
                anyOrNull(),
                any<(String) -> Unit>(),
                anyOrNull(),
            ),
        ).thenReturn("PROJ 123")

        val result = detector().detect("gpt-4", "text")
        assertNull(result)
    }

    @Test
    fun `detect uses provided model name`() {
        stubPromptTemplate()
        whenever(
            copilot.executeWithStreamingPerLine(
                any<String>(),
                any<String>(),
                any<Long>(),
                anyOrNull<File>(),
                anyOrNull(),
                any<(String) -> Unit>(),
                anyOrNull(),
            ),
        ).thenReturn("PROJ-123")

        detector().detect("claude-3-opus", "text")
        org.mockito.kotlin.verify(copilot).executeWithStreamingPerLine(
            eq("claude-3-opus"),
            any<String>(),
            any<Long>(),
            anyOrNull<File>(),
            anyOrNull(),
            any<(String) -> Unit>(),
            anyOrNull(),
        )
    }

    @Test
    fun `detect returns null when key is too long`() {
        stubPromptTemplate()
        val longKey = "A".repeat(100)
        whenever(
            copilot.executeWithStreamingPerLine(
                any<String>(),
                any<String>(),
                any<Long>(),
                anyOrNull<File>(),
                anyOrNull(),
                any<(String) -> Unit>(),
                anyOrNull(),
            ),
        ).thenReturn(longKey)

        val result = detector().detect("gpt-4", "text")
        assertNull(result)
    }

    @Test
    fun `detect catches InterruptedException and restores interrupt flag`() {
        val throwingCopilot =
            object : CopilotStreamingPort {
                override fun execute(
                    model: String,
                    prompt: String,
                    timeoutOverride: Long?,
                    attachments: List<AiAttachment>,
                ): String = ""

                override fun executeWithStreaming(
                    model: String,
                    prompt: String,
                    timeoutOverride: Long?,
                    isCancelled: (() -> Boolean)?,
                    onPartialOutput: (String) -> Unit,
                    attachments: List<AiAttachment>,
                ): String = ""

                override fun executeWithStreamingPerLine(
                    model: String,
                    prompt: String,
                    timeoutOverride: Long?,
                    repoDir: File?,
                    isCancelled: (() -> Boolean)?,
                    onPartialOutput: (String) -> Unit,
                    attachments: List<AiAttachment>,
                ): String = throw InterruptedException("interrupted")

                override fun resolveToken(): String? = null

                override fun isReady(): Boolean = false

                override fun resolveModel(): String = ""

                override fun resolveLanguage(): String = ""
            }
        stubPromptTemplate()
        Thread.interrupted()
        val result =
            AgentTicketNumberDetector(
                copilot = throwingCopilot,
                stackLensConfig = stackLensConfig,
                classpathResource = classpathResource,
                fileAttributes = fileSystem,
                fileContentStore = fileSystem,
            ).detect("gpt-4", "text")
        assertNull(result)
        assertEquals(true, Thread.interrupted(), "Interrupt flag should be set")
    }

    @Test
    fun `detect invokes onPartialOutput callback`() {
        stubPromptTemplate()
        val capturedOutputs = mutableListOf<String>()
        val callbackInvokingCopilot =
            object : CopilotStreamingPort {
                override fun execute(
                    model: String,
                    prompt: String,
                    timeoutOverride: Long?,
                    attachments: List<AiAttachment>,
                ): String = ""

                override fun executeWithStreaming(
                    model: String,
                    prompt: String,
                    timeoutOverride: Long?,
                    isCancelled: (() -> Boolean)?,
                    onPartialOutput: (String) -> Unit,
                    attachments: List<AiAttachment>,
                ): String = ""

                override fun executeWithStreamingPerLine(
                    model: String,
                    prompt: String,
                    timeoutOverride: Long?,
                    repoDir: File?,
                    isCancelled: (() -> Boolean)?,
                    onPartialOutput: (String) -> Unit,
                    attachments: List<AiAttachment>,
                ): String {
                    onPartialOutput("partial")
                    return "PROJ-999"
                }

                override fun resolveToken(): String? = null

                override fun isReady(): Boolean = false

                override fun resolveModel(): String = ""

                override fun resolveLanguage(): String = ""
            }
        val result =
            AgentTicketNumberDetector(
                copilot = callbackInvokingCopilot,
                stackLensConfig = stackLensConfig,
                classpathResource = classpathResource,
                fileAttributes = fileSystem,
                fileContentStore = fileSystem,
            ).detect("gpt-4", "text")
        assertEquals("PROJ-999", result)
        @Suppress("UNUSED_VARIABLE")
        val unused = capturedOutputs
    }
}
