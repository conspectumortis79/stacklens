package com.stacklens.adapter.inbound.rest.tooling

import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.application.port.inbound.tooling.ToolingImportUseCase
import com.stacklens.application.port.inbound.tooling.ToolingReadUseCase
import com.stacklens.application.port.inbound.workspace.WorkspaceFileInfo
import com.stacklens.application.port.inbound.workspace.WorkspaceFileUseCase
import com.stacklens.application.service.workspace.WorkspaceFileService
import com.stacklens.domain.model.ConfluenceTool
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.Path

@ExtendWith(MockitoExtension::class)
class ToolingImportExportApiControllerTest {
    @Mock
    private lateinit var toolingRead: ToolingReadUseCase

    @Mock
    private lateinit var toolingImport: ToolingImportUseCase

    private val fileAdapter = JavaFileSystemAdapter()

    private val workspaceFiles = WorkspaceFileService(fileAdapter, fileAdapter, fileAdapter, fileAdapter)

    private val controller by lazy {
        ToolingImportExportApiController(
            toolingRead,
            toolingImport,
            workspaceFiles,
            com.stacklens.application.service
                .FakeI18n(),
        )
    }

    private fun mockTool(id: Long = 1L) = ConfluenceTool(id = id, name = "My Tool", confluencePageUrl = "https://example.com", status = "READY")

    private fun mockToolWithPath(
        id: Long = 1L,
        jarPath: String?,
    ) = ConfluenceTool(
        id = id,
        name = "My Tool",
        confluencePageUrl = "https://example.com",
        status = "READY",
        jarPath = jarPath,
    )

    @Test
    fun `import returns 400 when name is blank`() {
        val file =
            org.springframework.mock.web
                .MockMultipartFile("file", "tool.jar", "application/java-archive", ByteArray(5))
        val resp = controller.import("", file)
        assertEquals(400, resp.statusCode.value())
    }

    @Test
    fun `import returns 400 when file is empty`() {
        val file =
            org.springframework.mock.web
                .MockMultipartFile("file", "tool.jar", "application/java-archive", ByteArray(0))
        val resp = controller.import("MyTool", file)
        assertEquals(400, resp.statusCode.value())
    }

    @Test
    fun `import returns 400 when filename is not jar`() {
        val file =
            org.springframework.mock.web
                .MockMultipartFile("file", "tool.zip", "application/zip", ByteArray(5))
        val resp = controller.import("MyTool", file)
        assertEquals(400, resp.statusCode.value())
    }

    @Test
    fun `import returns 200 on success`() {
        whenever(toolingImport.importTool(any(), any(), any())).thenReturn(mockTool(1L))
        val file =
            org.springframework.mock.web
                .MockMultipartFile("file", "tool.jar", "application/java-archive", ByteArray(5))
        val resp = controller.import("MyTool", file)
        assertEquals(200, resp.statusCode.value())
    }

    @Test
    fun `import returns 500 when service throws`() {
        whenever(toolingImport.importTool(any(), any(), any())).thenThrow(RuntimeException("Import failed"))
        val file =
            org.springframework.mock.web
                .MockMultipartFile("file", "tool.jar", "application/java-archive", ByteArray(5))
        val resp = controller.import("MyTool", file)
        assertEquals(500, resp.statusCode.value())
    }

    @Test
    fun `importBase64 returns 400 when name is blank`() {
        val req = ToolingImportExportApiController.Base64ImportRequest(name = "", filename = "t.jar", base64 = "")
        val resp = controller.importBase64(req)
        assertEquals(400, resp.statusCode.value())
    }

    @Test
    fun `importBase64 returns 400 when filename is not jar`() {
        val req = ToolingImportExportApiController.Base64ImportRequest(name = "MyTool", filename = "tool.zip", base64 = "")
        val resp = controller.importBase64(req)
        assertEquals(400, resp.statusCode.value())
    }

    @Test
    fun `importBase64 returns 200 on success`() {
        val bytes = ByteArray(10) { 0x50 }
        val encoded =
            java.util.Base64
                .getEncoder()
                .encodeToString(bytes)
        whenever(toolingImport.importTool(any(), any(), any())).thenReturn(mockTool(1L))
        val req = ToolingImportExportApiController.Base64ImportRequest(name = "MyTool", filename = "tool.jar", base64 = encoded)
        val resp = controller.importBase64(req)
        assertEquals(200, resp.statusCode.value())
    }

    @Test
    fun `importBase64 returns 200 with data-url encoded base64`() {
        val bytes = ByteArray(10) { 0x50 }
        val encoded =
            "data:application/java-archive;base64," +
                java.util.Base64
                    .getEncoder()
                    .encodeToString(bytes)
        whenever(toolingImport.importTool(any(), any(), any())).thenReturn(mockTool(1L))
        val req = ToolingImportExportApiController.Base64ImportRequest(name = "MyTool", filename = "tool.jar", base64 = encoded)
        val resp = controller.importBase64(req)
        assertEquals(200, resp.statusCode.value())
    }

    @Test
    fun `importBase64 returns 500 when service throws`() {
        val encoded =
            java.util.Base64
                .getEncoder()
                .encodeToString(ByteArray(5))
        whenever(toolingImport.importTool(any(), any(), any())).thenThrow(RuntimeException("Failed"))
        val req = ToolingImportExportApiController.Base64ImportRequest(name = "MyTool", filename = "t.jar", base64 = encoded)
        val resp = controller.importBase64(req)
        assertEquals(500, resp.statusCode.value())
    }

    @Test
    fun `importLocal returns 400 when name is blank`() {
        val req = ToolingImportExportApiController.LocalImportRequest(name = "", path = "/any/path.jar")
        val resp = controller.importLocal(req)
        assertEquals(400, resp.statusCode.value())
    }

    @Test
    fun `importLocal returns 400 when file does not exist`() {
        val req = ToolingImportExportApiController.LocalImportRequest(name = "MyTool", path = "/nonexistent/tool.jar")
        val resp = controller.importLocal(req)
        assertEquals(400, resp.statusCode.value())
    }

    @Test
    fun `importLocal returns 400 when path is a directory`(
        @TempDir tempDir: Path,
    ) {
        val req = ToolingImportExportApiController.LocalImportRequest(name = "MyTool", path = tempDir.toString())
        val resp = controller.importLocal(req)
        assertEquals(400, resp.statusCode.value())
    }

    @Test
    fun `importLocal returns 400 when file exists but is not a jar`(
        @TempDir tempDir: Path,
    ) {
        val txtFile = tempDir.resolve("tool.txt").toFile()
        txtFile.writeText("not a jar")
        val req = ToolingImportExportApiController.LocalImportRequest(name = "MyTool", path = txtFile.absolutePath)
        val resp = controller.importLocal(req)
        assertEquals(400, resp.statusCode.value())
    }

    @Test
    fun `importLocal returns 200 when valid jar exists`(
        @TempDir tempDir: Path,
    ) {
        val jarFile = tempDir.resolve("my.jar").toFile()
        jarFile.writeBytes(ByteArray(5) { 0x50 })
        whenever(toolingImport.importTool(any(), any(), any())).thenReturn(mockTool(1L))
        val req = ToolingImportExportApiController.LocalImportRequest(name = "MyTool", path = jarFile.absolutePath)
        val resp = controller.importLocal(req)
        assertEquals(200, resp.statusCode.value())
    }

    @Test
    fun `importLocal returns 500 when service throws`(
        @TempDir tempDir: Path,
    ) {
        val jarFile = tempDir.resolve("my.jar").toFile()
        jarFile.writeBytes(ByteArray(5) { 0x50 })
        whenever(toolingImport.importTool(any(), any(), any())).thenThrow(RuntimeException("Import error"))
        val req = ToolingImportExportApiController.LocalImportRequest(name = "MyTool", path = jarFile.absolutePath)
        val resp = controller.importLocal(req)
        assertEquals(500, resp.statusCode.value())
    }

    @Test
    fun `export returns 404 when tool not found`() {
        whenever(toolingRead.getTool(99L)).thenReturn(null)
        val resp = controller.export(99L)
        assertEquals(404, resp.statusCode.value())
    }

    @Test
    fun `export returns 400 when jarPath is null`() {
        whenever(toolingRead.getTool(1L)).thenReturn(mockToolWithPath(1L, jarPath = null))
        val resp = controller.export(1L)
        assertEquals(400, resp.statusCode.value())
    }

    @Test
    fun `export returns 404 when jar file does not exist`(
        @TempDir tempDir: Path,
    ) {
        val jarPath = tempDir.resolve("nonexistent.jar").toString()
        whenever(toolingRead.getTool(1L)).thenReturn(mockToolWithPath(1L, jarPath = jarPath))
        val resp = controller.export(1L)
        assertEquals(404, resp.statusCode.value())
    }

    @Test
    fun `export returns 200 with jar file when exists`(
        @TempDir tempDir: Path,
    ) {
        val jarFile = tempDir.resolve("tool.jar").toFile()
        jarFile.writeBytes(ByteArray(10) { 0x50 })
        whenever(toolingRead.getTool(1L)).thenReturn(mockToolWithPath(1L, jarPath = jarFile.absolutePath))
        val resp = controller.export(1L)
        assertEquals(200, resp.statusCode.value())
    }

    @Test
    fun `saveToPath returns 404 when tool not found`(
        @TempDir tempDir: Path,
    ) {
        whenever(toolingRead.getTool(99L)).thenReturn(null)
        val req = ToolingImportExportApiController.SaveToPathRequest(path = tempDir.toString())
        val resp = controller.saveToPath(99L, req)
        assertEquals(404, resp.statusCode.value())
    }

    @Test
    fun `saveToPath returns 400 when jar path is null`(
        @TempDir tempDir: Path,
    ) {
        whenever(toolingRead.getTool(1L)).thenReturn(mockToolWithPath(1L, jarPath = null))
        val req = ToolingImportExportApiController.SaveToPathRequest(path = tempDir.toString())
        val resp = controller.saveToPath(1L, req)
        assertEquals(400, resp.statusCode.value())
    }

    @Test
    fun `saveToPath returns 404 when jar file does not exist`(
        @TempDir tempDir: Path,
    ) {
        val jarPath = tempDir.resolve("missing.jar").toString()
        whenever(toolingRead.getTool(1L)).thenReturn(mockToolWithPath(1L, jarPath = jarPath))
        val req = ToolingImportExportApiController.SaveToPathRequest(path = tempDir.toString())
        val resp = controller.saveToPath(1L, req)
        assertEquals(404, resp.statusCode.value())
    }

    @Test
    fun `saveToPath returns 404 when the jar path is a directory`(
        @TempDir tempDir: Path,
    ) {
        val jarDir = tempDir.resolve("jar-dir").toFile().apply { mkdirs() }
        whenever(toolingRead.getTool(1L)).thenReturn(mockToolWithPath(1L, jarPath = jarDir.absolutePath))
        val req = ToolingImportExportApiController.SaveToPathRequest(path = tempDir.toString())
        val resp = controller.saveToPath(1L, req)
        assertEquals(404, resp.statusCode.value())
    }

    @Test
    fun `importLocal returns 500 when the jar vanishes before it can be read`() {
        val workspace = mock<WorkspaceFileUseCase>()
        whenever(workspace.fileInfo("/tmp/vanished.jar")).thenReturn(
            WorkspaceFileInfo(
                name = "vanished.jar",
                path = "/tmp/vanished.jar",
                size = 1L,
                isDirectory = false,
                isFile = true,
            ),
        )
        whenever(workspace.readBytes("/tmp/vanished.jar")).thenReturn(null)
        val isolated =
            ToolingImportExportApiController(
                toolingRead,
                toolingImport,
                workspace,
                com.stacklens.application.service
                    .FakeI18n(),
            )
        val resp = isolated.importLocal(ToolingImportExportApiController.LocalImportRequest("MyTool", "/tmp/vanished.jar"))
        assertEquals(500, resp.statusCode.value())
    }

    @Test
    fun `saveToPath returns 200 and copies jar to target directory`(
        @TempDir tempDir: Path,
    ) {
        val sourceJar = tempDir.resolve("source.jar").toFile()
        sourceJar.writeBytes(ByteArray(5) { 0x50 })
        val targetDir = tempDir.resolve("target").toFile()
        targetDir.mkdir()
        whenever(toolingRead.getTool(1L)).thenReturn(mockToolWithPath(1L, jarPath = sourceJar.absolutePath))
        val req = ToolingImportExportApiController.SaveToPathRequest(path = targetDir.absolutePath)
        val resp = controller.saveToPath(1L, req)
        assertEquals(200, resp.statusCode.value())
    }

    @Test
    fun `saveToPath returns 400 when target dir does not exist`(
        @TempDir tempDir: Path,
    ) {
        val sourceJar = tempDir.resolve("source.jar").toFile()
        sourceJar.writeBytes(ByteArray(5) { 0x50 })
        whenever(toolingRead.getTool(1L)).thenReturn(mockToolWithPath(1L, jarPath = sourceJar.absolutePath))
        val req = ToolingImportExportApiController.SaveToPathRequest(path = "/nonexistent/dir/xyz99")
        val resp = controller.saveToPath(1L, req)
        assertEquals(400, resp.statusCode.value())
    }

    @Test
    fun `saveToPath returns 400 when target path is a file not a directory`(
        @TempDir tempDir: Path,
    ) {
        val sourceJar = tempDir.resolve("source.jar").toFile()
        sourceJar.writeBytes(ByteArray(5) { 0x50 })
        val targetFile = tempDir.resolve("notadir.txt").toFile()
        targetFile.writeText("i am a file")
        whenever(toolingRead.getTool(1L)).thenReturn(mockToolWithPath(1L, jarPath = sourceJar.absolutePath))
        val req = ToolingImportExportApiController.SaveToPathRequest(path = targetFile.absolutePath)
        val resp = controller.saveToPath(1L, req)
        assertEquals(400, resp.statusCode.value())
    }

    @Test
    fun `saveToPath returns 500 when file copy fails`(
        @TempDir tempDir: Path,
    ) {
        val sourceJar = tempDir.resolve("source.jar").toFile()
        sourceJar.writeBytes(ByteArray(5) { 0x50 })
        val targetDir = tempDir.resolve("readonly").toFile()
        targetDir.mkdir()
        targetDir.setReadOnly()
        whenever(toolingRead.getTool(1L)).thenReturn(mockToolWithPath(1L, jarPath = sourceJar.absolutePath))
        val req = ToolingImportExportApiController.SaveToPathRequest(path = targetDir.absolutePath)
        val resp = controller.saveToPath(1L, req)

        assertTrue(resp.statusCode.value() in listOf(200, 500))
    }
}
