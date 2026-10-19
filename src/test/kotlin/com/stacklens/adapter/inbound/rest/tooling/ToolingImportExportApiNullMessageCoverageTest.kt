package com.stacklens.adapter.inbound.rest.tooling

import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.application.port.inbound.tooling.ToolingImportUseCase
import com.stacklens.application.port.inbound.tooling.ToolingReadUseCase
import com.stacklens.application.service.workspace.WorkspaceFileService
import com.stacklens.domain.model.ConfluenceTool
import com.stacklens.testsupport.FakeI18n
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockMultipartFile
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Path

class ToolingImportExportApiNullMessageCoverageTest {
    private val toolingRead: ToolingReadUseCase = mock()
    private val toolingImport: ToolingImportUseCase = mock()

    private val controller =
        ToolingImportExportApiController(
            toolingRead,
            toolingImport,
            WorkspaceFileService(JavaFileSystemAdapter(), JavaFileSystemAdapter(), JavaFileSystemAdapter(), JavaFileSystemAdapter()),
            FakeI18n(),
        )

    private val tool = ConfluenceTool(id = 4L, name = "Demo/Tool", confluencePageUrl = "https://example.com", status = "READY")

    @Test
    fun `import reports an empty reason when the upload blows up without a message`() {
        val failing =
            object : MockMultipartFile("file", "tool.jar", "application/java-archive", ByteArray(3)) {
                override fun getBytes(): ByteArray = throw RuntimeException()
            }

        val resp = controller.import("Demo", failing)

        assertEquals(500, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        assertEquals("i18n:tooling.be_import_failed|", (resp.body as Map<String, Any?>)["error"])
    }

    @Test
    fun `importBase64 reports an empty reason when the service fails without a message`() {
        whenever(toolingImport.importTool(any(), any(), any())).thenThrow(RuntimeException())

        val resp =
            controller.importBase64(
                ToolingImportExportApiController.Base64ImportRequest(
                    name = "Demo",
                    filename = "tool.jar",
                    base64 = "data:application/java-archive;base64,QUJD",
                ),
            )

        assertEquals(500, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        assertEquals("i18n:tooling.be_import_failed|", (resp.body as Map<String, Any?>)["error"])
    }

    @Test
    fun `importLocal reports an empty reason when the service fails without a message`(
        @TempDir dir: Path,
    ) {
        whenever(toolingImport.importTool(any(), any(), any())).thenThrow(RuntimeException())
        val jar = dir.resolve("tool.jar").toFile().apply { writeText("abc") }

        val resp = controller.importLocal(ToolingImportExportApiController.LocalImportRequest("Demo", jar.absolutePath))

        assertEquals(500, resp.statusCode.value())
    }

    @Test
    fun `import accepts an upper-case jar suffix`() {
        val file: MultipartFile = MockMultipartFile("file", "TOOL.JAR", "application/java-archive", ByteArray(2))
        whenever(toolingImport.importTool(any(), any(), any())).thenReturn(tool)

        assertEquals(200, controller.import("Demo", file).statusCode.value())
    }

    @Test
    fun `saveToPath with an unknown tool id returns not found`(
        @TempDir dir: Path,
    ) {
        whenever(toolingRead.getTool(9L)).thenReturn(null)

        val resp = controller.saveToPath(9L, ToolingImportExportApiController.SaveToPathRequest(dir.toString()))

        assertEquals(404, resp.statusCode.value())
        assertEquals(null, resp.body)
    }

    @Test
    fun `saveToPath without a stored jar reports the missing-jar error`(
        @TempDir dir: Path,
    ) {
        whenever(toolingRead.getTool(4L)).thenReturn(tool.apply { jarPath = null })

        val resp = controller.saveToPath(4L, ToolingImportExportApiController.SaveToPathRequest(dir.toString()))

        assertEquals(400, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        assertEquals("i18n:tooling.be_no_jar", (resp.body as Map<String, Any?>)["error"])
    }
}
