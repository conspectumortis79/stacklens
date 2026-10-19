package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.application.port.inbound.review.ReviewAnalysisDecodingUseCase
import com.stacklens.application.port.inbound.review.ReviewAnalysisUseCase
import com.stacklens.application.port.inbound.workspace.WorkspaceDirectoryEntry
import com.stacklens.application.port.inbound.workspace.WorkspaceFileUseCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.mock
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewAnalysisControllerBrowseFileEntriesTest {
    @Mock
    lateinit var analysisService: ReviewAnalysisUseCase

    @Mock
    lateinit var i18n: I18nUseCase

    @Mock
    lateinit var decoding: ReviewAnalysisDecodingUseCase

    private val workspaceFiles: WorkspaceFileUseCase = mock()

    private val controller by lazy {
        ReviewAnalysisController(
            analysisService = analysisService,
            i18n = i18n,
            saveHandlers = emptyList(),
            decoding = decoding,
            workspaceFiles = workspaceFiles,
        )
    }

    private fun directory(name: String) = WorkspaceDirectoryEntry(name = name, path = "/tmp/$name", isDirectory = true, isFile = false, size = 0L)

    private fun file(name: String) = WorkspaceDirectoryEntry(name = name, path = "/tmp/$name", isDirectory = false, isFile = true, size = 1L)

    private fun ghost(name: String) = WorkspaceDirectoryEntry(name = name, path = "/tmp/$name", isDirectory = false, isFile = false, size = 0L)

    @Test
    fun `browseFileEntries returns directories before files sorted alphabetically`() {
        val entries =
            controller.browseFileEntries(
                listOf(file("aaa.md"), directory("zzz-dir"), file("bbb.html"), directory("mmm-dir")),
                directoriesOnly = false,
            )
        assertEquals(listOf("mmm-dir", "zzz-dir", "aaa.md", "bbb.html"), entries.map { it["name"] })
        assertEquals(listOf("directory", "directory", "file", "file"), entries.map { it["type"] })
    }

    @Test
    fun `browseFileEntries with directoriesOnly=true excludes all files`() {
        val entries =
            controller.browseFileEntries(
                listOf(directory("d1"), directory("d2"), file("file.md")),
                directoriesOnly = true,
            )
        assertEquals(listOf("d1", "d2"), entries.map { it["name"] })
        assertEquals(setOf("directory"), entries.map { it["type"] }.toSet())
    }

    @Test
    fun `browseFileEntries includes only md and html files`() {
        val entries =
            controller.browseFileEntries(
                listOf(file("doc.md"), file("page.html"), file("text.txt"), file("data.json")),
                directoriesOnly = false,
            )
        assertEquals(setOf("doc.md", "page.html"), entries.map { it["name"] }.toSet())
    }

    @Test
    fun `browseFileEntries accepts case-insensitive md html extensions`() {
        val entries =
            controller.browseFileEntries(
                listOf(file("UPPER.MD"), file("lower.md"), file("UPPER.HTML")),
                directoriesOnly = false,
            )
        assertEquals(3, entries.size)
    }

    @Test
    fun `browseFileEntries handles an empty directory`() {
        assertEquals(emptyList<Map<String, Any?>>(), controller.browseFileEntries(emptyList(), directoriesOnly = false))
    }

    @Test
    fun `browseFileEntries skips entries that are neither files nor directories`() {
        val entries =
            controller.browseFileEntries(
                listOf(directory("kept"), ghost("dangling"), file("doc.md")),
                directoriesOnly = false,
            )
        assertEquals(setOf("kept", "doc.md"), entries.map { it["name"] }.toSet())
    }
}
