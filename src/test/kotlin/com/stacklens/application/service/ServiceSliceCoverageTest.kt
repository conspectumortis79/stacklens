package com.stacklens.application.service

import com.stacklens.adapter.out.demo.DemoTicketSimulator
import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.adapter.out.image.ImageInlinePreparer
import com.stacklens.application.port.inbound.chat.ChatAttachment
import com.stacklens.application.port.inbound.imageextraction.ImageExtractionRequest
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.ImageExtractionPort
import com.stacklens.application.port.out.MrFetchPort
import com.stacklens.application.port.out.TicketCreationRecordPort
import com.stacklens.application.port.out.TranslationCatalogPort
import com.stacklens.application.port.out.ai.AiUsageContextPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.application.service.imageextraction.ImageExtractionAi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

class ServiceSliceCoverageTest {
    @Test
    fun `the attachment preparer knows every supported image mime type`() {
        val preparer = KiChatAttachmentPreparer(mock<ImageInlinePreparer>())

        assertTrue(preparer.isSupportedMimeType("IMAGE/PNG"))
        assertTrue(preparer.isSupportedMimeType("image/jpeg"))
        assertTrue(preparer.isSupportedMimeType("image/jpg"))
        assertEquals(false, preparer.isSupportedMimeType("application/pdf"))
    }

    @Test
    fun `translations fall back to german and format placeholders only when needed`() {
        val catalog = mock<TranslationCatalogPort>()
        whenever(catalog.load("en")).thenReturn(mapOf("greeting" to "Hello {0}", "plain" to "No placeholder"))
        whenever(catalog.load("de")).thenReturn(mapOf("greeting" to "Hallo {0}"))
        val aiConfigRepo = mock<AiConfigPort>()
        val service = I18nService(catalog, aiConfigRepo)

        assertEquals("Hello Ada", service.tFor("en", "greeting", "Ada"))
        assertEquals("No placeholder", service.tFor("en", "plain", "Ada"))
        assertEquals("Hallo Ada", service.tFor("de", "greeting", "Ada"))
        assertEquals("unknown.key", service.tFor("en", "unknown.key"))
        assertEquals("de", service.resolveLanguage())
    }

    @Test
    fun `a simulated ticket without history produces a random project key`() {
        val recordPort = mock<TicketCreationRecordPort>()
        val simulator = DemoTicketSimulator(recordPort)

        val result = simulator.simulate(jiraConfigId = 3L, projectKey = "proj", issueType = "Task", labels = listOf("demo"), historyId = null, configName = "Demo Jira", subtasks = emptyList())

        assertTrue(result.values.any { it.toString().matches(Regex("PROJ-\\d{4}")) }, result.toString())
    }

    @Test
    fun `the prompt assembler lists changed files even without a jira ticket`() {
        val i18n = mock<com.stacklens.application.port.out.i18n.I18nPort>()
        whenever(i18n.tFor(any(), any())).thenReturn("")
        whenever(i18n.tFor(any(), any(), any())).thenReturn("")
        val copilotPort = mock<CopilotPort>()
        whenever(copilotPort.resolveLanguage()).thenReturn("en")
        val promptLoader = mock<PromptReaderPort>()
        whenever(promptLoader.get(any(), any(), any())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            (invocation.arguments[2] as Map<String, String>).getValue("changedFilesList")
        }
        val assembler = CodeQaPromptAssembler(i18n, copilotPort, promptLoader)
        val mr =
            MrFetchPort.MrData(
                mrUrl = "https://gitlab/mr/1",
                platform = "gitlab",
                title = "Feature",
                description = "desc",
                sourceBranch = "feat",
                targetBranch = "main",
                authorName = "ada",
                state = "opened",
                diffText = "diff",
                changedFiles = listOf("src/Main.kt"),
            )

        val prompt = assembler.buildPrompt(mr, null)

        assertTrue(prompt.contains("- src/Main.kt"), prompt)
    }

    @Test
    fun `extracting from a server path that is a directory is rejected`() {
        val service =
            ImageExtractionService(
                mock<ImageExtractionAi>(),
                mock<AiUsageContextPort>(),
                mock<ImageExtractionPort>(),
                mock<StartupHealer>(),
                fileSystem = JavaFileSystemAdapter(),
            )
        val directory = File(System.getProperty("java.io.tmpdir")).absolutePath

        val failure =
            assertThrows(IllegalArgumentException::class.java) {
                service.extractFromServerFile(
                    ImageExtractionRequest(
                        sourcePath = directory,
                        originalFileName = "shot.png",
                        mimeType = "image/png",
                        fileSizeBytes = 10L,
                    ),
                )
            }

        assertTrue(failure.message!!.contains("Quelldatei nicht gefunden"), failure.message)
    }

    @Test
    fun `chat attachments keep their mime type when supported`() {
        val preparer = KiChatAttachmentPreparer(mock<ImageInlinePreparer>())

        assertEquals(null, preparer.prepareOne(ChatAttachment(mimeType = "application/pdf", base64Data = "eHl6", name = "doc.pdf")))
    }
}
