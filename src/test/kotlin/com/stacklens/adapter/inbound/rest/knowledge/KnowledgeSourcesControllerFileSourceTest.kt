package com.stacklens.adapter.inbound.rest.knowledge

import com.stacklens.application.port.inbound.knowledge.KnowledgeSourceUseCase
import com.stacklens.domain.model.knowledge.KnowledgeSource
import com.stacklens.domain.model.knowledge.KnowledgeSourceType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockMultipartFile
import java.io.File
import java.io.IOException
import java.nio.file.Path

class KnowledgeSourcesControllerFileSourceTest {
    private val sourceUseCase: KnowledgeSourceUseCase = mock()

    private val controller = KnowledgeSourcesController(sourceUseCase)

    private val created =
        KnowledgeSource(
            id = 11L,
            knowledgeBaseId = 3L,
            name = "handbook",
            type = KnowledgeSourceType.FILE,
        )

    @Test
    fun `uploadFileSource forwards the multipart name and bytes to the use case`() {
        val file = MockMultipartFile("file", "handbook.pdf", "application/pdf", byteArrayOf(1, 2, 3))
        whenever(sourceUseCase.createFileSource(3L, "handbook.pdf", byteArrayOf(1, 2, 3))).thenReturn(created)

        val result = controller.uploadFileSource(knowledgeBaseId = 3L, file = file)

        assertEquals(created, result)
    }

    @Test
    fun `uploadFileSource fails when the multipart has no original filename`() {
        val file =
            object : org.springframework.web.multipart.MultipartFile by MockMultipartFile("file", "x.pdf", "application/pdf", byteArrayOf(1)) {
                override fun getOriginalFilename(): String? = null
            }

        val failure =
            assertThrows(IOException::class.java) {
                controller.uploadFileSource(knowledgeBaseId = 3L, file = file)
            }

        assertEquals("uploaded file has no name", failure.message)
    }

    @Test
    fun `uploadLocalFileSource reads an allowed local file`(
        @TempDir tmp: Path,
    ) {
        val local = File(tmp.toFile(), "notes.MD").apply { writeText("# title") }
        whenever(sourceUseCase.createFileSource(eq(5L), eq("notes.MD"), any())).thenReturn(created)

        val result = controller.uploadLocalFileSource(LocalFileSourceRequest(knowledgeBaseId = 5L, path = local.absolutePath))

        assertEquals(created, result)
        verify(sourceUseCase).createFileSource(5L, "notes.MD", "# title".toByteArray())
    }

    @Test
    fun `uploadLocalFileSource rejects a missing file`(
        @TempDir tmp: Path,
    ) {
        val missing = File(tmp.toFile(), "absent.pdf")

        val failure =
            assertThrows(IllegalArgumentException::class.java) {
                controller.uploadLocalFileSource(LocalFileSourceRequest(1L, missing.absolutePath))
            }

        assertTrue(failure.message!!.contains("Datei nicht gefunden"), "unexpected: ${failure.message}")
    }

    @Test
    fun `uploadLocalFileSource rejects a directory`(
        @TempDir tmp: Path,
    ) {
        val directory = File(tmp.toFile(), "folder.pdf").apply { mkdirs() }

        val failure =
            assertThrows(IllegalArgumentException::class.java) {
                controller.uploadLocalFileSource(LocalFileSourceRequest(1L, directory.absolutePath))
            }

        assertTrue(failure.message!!.contains("Datei nicht gefunden"), "unexpected: ${failure.message}")
    }

    @Test
    fun `uploadLocalFileSource rejects a file without a supported extension`(
        @TempDir tmp: Path,
    ) {
        val script = File(tmp.toFile(), "run.sh").apply { writeText("echo hi") }

        val failure =
            assertThrows(IllegalArgumentException::class.java) {
                controller.uploadLocalFileSource(LocalFileSourceRequest(1L, script.absolutePath))
            }

        assertTrue(failure.message!!.contains("Ungültige Dateiendung"), "unexpected: ${failure.message}")
        assertTrue(failure.message!!.contains("markdown"), "the message must list the accepted extensions: ${failure.message}")
    }

    @Test
    fun `createJiraSprintSource forwards the sprint configuration with its defaults`() {
        whenever(sourceUseCase.createJiraSprintSource(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(created)

        val result =
            controller.createJiraSprintSource(
                CreateJiraSprintSourceRequest(
                    knowledgeBaseId = 7L,
                    jiraConfigId = 2L,
                    sprintReference = "PROJ-27",
                ),
            )

        assertEquals(created, result)
        verify(sourceUseCase).createJiraSprintSource(
            knowledgeBaseId = 7L,
            jiraConfigId = 2L,
            sprintReference = "PROJ-27",
            includeChildren = true,
            includeComments = true,
            maxDepth = 1,
            includeLinkedTickets = true,
            includeParent = false,
        )
    }

    @Test
    fun `createJiraSprintSource forwards explicitly disabled flags`() {
        whenever(sourceUseCase.createJiraSprintSource(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(created)

        controller.createJiraSprintSource(
            CreateJiraSprintSourceRequest(
                knowledgeBaseId = 8L,
                jiraConfigId = 4L,
                sprintReference = "PROJ-28",
                includeChildren = false,
                includeComments = false,
                maxDepth = 3,
                includeLinkedTickets = false,
                includeParent = true,
            ),
        )

        verify(sourceUseCase).createJiraSprintSource(8L, 4L, "PROJ-28", false, false, 3, false, true)
    }

    @Test
    fun `createManualSource forwards the request payload to the use case`() {
        whenever(sourceUseCase.createManualSource(1L, "manual", "text", "ref")).thenReturn(created)

        val manual =
            controller.createManualSource(
                CreateManualSourceRequest(knowledgeBaseId = 1L, name = "manual", content = "text", externalReference = "ref"),
            )

        assertEquals(created, manual)
    }

    @Test
    fun `createConfluenceSource forwards the request payload to the use case`() {
        whenever(sourceUseCase.createConfluenceSource(1L, 2L, "https://page", true, 10)).thenReturn(created)

        val confluence =
            controller.createConfluenceSource(
                CreateConfluenceSourceRequest(knowledgeBaseId = 1L, confluenceConfigId = 2L, rootPageUrl = "https://page"),
            )

        assertEquals(created, confluence)
    }

    @Test
    fun `createJiraSource converts the raw ticket key and keeps the flags`() {
        whenever(
            sourceUseCase.createJiraSource(
                knowledgeBaseId = eq(1L),
                jiraConfigId = eq(2L),
                rootTicketKey = any(),
                includeChildren = eq(true),
                includeComments = eq(true),
                maxDepth = eq(10),
                includeLinkedTickets = eq(true),
                includeParent = eq(false),
            ),
        ).thenReturn(created)

        val jira =
            controller.createJiraSource(
                CreateJiraSourceRequest(knowledgeBaseId = 1L, jiraConfigId = 2L, rootTicketKey = "PROJ-1"),
            )

        assertEquals(created, jira)
        verify(sourceUseCase)
            .createJiraSource(
                knowledgeBaseId = eq(1L),
                jiraConfigId = eq(2L),
                rootTicketKey = any(),
                includeChildren = eq(true),
                includeComments = eq(true),
                maxDepth = eq(10),
                includeLinkedTickets = eq(true),
                includeParent = eq(false),
            )
    }

    @Test
    fun `request payloads keep their values`() {
        val sprint = CreateJiraSprintSourceRequest(1L, 2L, "S", includeParent = true)
        val local = LocalFileSourceRequest(3L, "/tmp/x.pdf")

        assertEquals(
            listOf(1L, 2L, "S", true, true, 1, true, true),
            listOf(
                sprint.knowledgeBaseId,
                sprint.jiraConfigId,
                sprint.sprintReference,
                sprint.includeChildren,
                sprint.includeComments,
                sprint.maxDepth,
                sprint.includeLinkedTickets,
                sprint.includeParent,
            ),
        )
        assertEquals(listOf(3L, "/tmp/x.pdf"), listOf(local.knowledgeBaseId, local.path))
        assertEquals("CreateJiraSprintSourceRequest", sprint.toString().substringBefore("("))
        assertTrue(sprint.copy(maxDepth = 5) != sprint)
        assertTrue(sprint.hashCode() != sprint.copy(includeParent = false).hashCode())
    }
}
