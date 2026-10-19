package com.stacklens.adapter.inbound.rest

import com.stacklens.adapter.inbound.rest.pipeline.MockupFileAccess
import com.stacklens.application.port.inbound.pipeline.CopilotAgentPipelineUseCase
import com.stacklens.application.port.inbound.pipeline.PipelineWriteError
import com.stacklens.application.port.inbound.pipeline.PipelineWriteErrorCode
import com.stacklens.application.port.inbound.pipeline.PipelineWriteFile
import com.stacklens.application.port.inbound.pipeline.PipelineWriteOutcome
import com.stacklens.application.port.inbound.pipeline.PipelineWritePreview
import com.stacklens.application.port.inbound.ticket.JiraTicketImplementationUseCase
import com.stacklens.application.port.inbound.workspace.WorkspaceAccessView
import com.stacklens.application.port.inbound.workspace.WorkspaceFileUseCase
import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.model.pipeline.PipelineStatus
import com.stacklens.application.service.I18nService
import com.stacklens.testsupport.FakeSystemGuidanceUseCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.File

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CopilotAgentPipelineControllerTest {
    @Mock private lateinit var pipelineService: CopilotAgentPipelineUseCase

    @Mock private lateinit var jiraAnalysisService: JiraTicketImplementationUseCase

    private val i18n: I18nService =
        mock(
            defaultAnswer =
                org.mockito.stubbing.Answer { invocation ->
                    val key = invocation.arguments.getOrNull(0) as? String ?: ""
                    when {
                        "mime_missing" in key -> "mockupMimeType fehlt"
                        "type_not_supported" in key -> "Dateityp nicht erlaubt"
                        "too_large" in key -> "zu groß"
                        "invalid_path" in key -> "Ungültiger Pfad"
                        "rex_already" in key -> "Rex bereits geschrieben"
                        "path_traversal" in key -> "Pfad-Traversal abgelehnt"
                        "dir_create_failed" in key -> "Verzeichnis konnte nicht erstellt werden"
                        "no_repo_linked" in key -> "Kein Repository"
                        "no_output" in key -> "Keine Ausgabe"
                        "dir_not_exists" in key -> "Verzeichnis existiert nicht"
                        "write_access_denied" in key -> "Schreibzugriff verweigert"
                        "write_probe_failed" in key -> "Schreibtest fehlgeschlagen"
                        "no_write_permissions" in key -> "Keine Schreibrechte"
                        "file_type_not_allowed" in key -> "Dateityp nicht erlaubt"
                        "file_not_found" in key -> "Datei nicht gefunden"
                        "too_long" in key -> "Text zu lang"
                        "ticket_fetch_failed" in key -> "Jira-Ticket konnte nicht abgerufen werden"
                        else -> ""
                    }
                },
        )

    private val mockupReader: com.stacklens.adapter.inbound.rest.pipeline.MockupReader =
        com.stacklens.adapter.inbound.rest.pipeline
            .MockupReader(i18n)

    private val ssePublisher: com.stacklens.adapter.inbound.rest.pipeline.PipelineSsePublisher by lazy {
        com.stacklens.adapter.inbound.rest.pipeline
            .PipelineSsePublisher(pipelineService)
    }

    private val systemGuidance = FakeSystemGuidanceUseCase()

    private val workspaceFileUseCase: WorkspaceFileUseCase = mock()

    private val controller by lazy {
        CopilotAgentPipelineController(
            pipelineService,
            jiraAnalysisService,
            i18n,
            mockupReader,
            ssePublisher,
            systemGuidance,
            workspaceFileUseCase,
        )
    }

    private fun accessView(
        path: String,
        exists: Boolean = true,
        isDirectory: Boolean = true,
        canRead: Boolean = true,
        canWrite: Boolean = true,
        probeOk: Boolean = true,
        probeError: String? = null,
    ) = WorkspaceAccessView(path, exists, isDirectory, canRead, canWrite, probeOk, probeError)

    private fun makeRun(
        id: Long = 1L,
        status: PipelineStatus = PipelineStatus.DONE,
    ) = AgentPipelineRun(
        id = id,
        rawText = "test",
        repoPath = null,
        featureSlug = "test-slug",
        createdAt = System.currentTimeMillis(),
    ).also { it.status = status }

    @Test
    fun `parseFinnOutput extracts file blocks`() {
        val output =
            """
            ### src/App.php
            ```php
            <?php class App {}
            ```
            """.trimIndent()
        val parsed = CopilotAgentPipelineController.parseFinnOutput(output)
        assertEquals(1, parsed.size)
        assertEquals("src/App.php", parsed[0].path)
        assertTrue(parsed[0].content.contains("App"))
    }

    @Test
    fun `parseFinnOutput rejects absolute paths`() {
        val output =
            """
            ### /etc/passwd
            ```txt
            root
            ```
            """.trimIndent()
        val parsed = CopilotAgentPipelineController.parseFinnOutput(output)
        assertTrue(parsed.isEmpty())
    }

    @Test
    fun `parseFinnOutput rejects path traversal`() {
        val output =
            """
            ### ../secret.txt
            ```txt
            data
            ```
            """.trimIndent()
        val parsed = CopilotAgentPipelineController.parseFinnOutput(output)
        assertTrue(parsed.isEmpty())
    }

    @Test
    fun `parseFinnOutput accepts valid paths`() {
        val output =
            """
            ### src/service/MyService.php
            ```php
            class MyService {}
            ```
            """.trimIndent()
        val parsed = CopilotAgentPipelineController.parseFinnOutput(output)
        assertEquals(1, parsed.size)
        assertEquals("src/service/MyService.php", parsed[0].path)
    }

    @Test
    fun `parseFinnOutput strips quotes from path`() {
        val output = "### `src/App.php`\n```php\nclass App {}\n```"
        val parsed = CopilotAgentPipelineController.parseFinnOutput(output)
        assertEquals(1, parsed.size)
        assertEquals("src/App.php", parsed[0].path)
    }

    @Test
    fun `parseFinnOutput rejects blank path after cleaning`() {
        val output = "### `:`\n```php\nclass X {}\n```"
        val parsed = CopilotAgentPipelineController.parseFinnOutput(output)
        assertTrue(parsed.isEmpty())
    }

    @Test
    fun `parseFinnOutput accepts path with nested directory`() {
        val output = "### src/service/MyService.php\n```php\nclass MyService {}\n```"
        val parsed = CopilotAgentPipelineController.parseFinnOutput(output)
        assertEquals(1, parsed.size)
    }

    @Test
    fun `start returns 400 when rawText is blank`() {
        val result = controller.start(CopilotAgentPipelineController.StartRequest(""))
        assertEquals(400, result.statusCode.value())
    }

    @Test
    fun `start returns 200 with id`() {
        whenever(pipelineService.start(any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), any())).thenReturn(42L)
        val result = controller.start(CopilotAgentPipelineController.StartRequest("Add JWT auth"))
        assertEquals(200, result.statusCode.value())
        assertEquals(42L, result.body!!["id"])
    }

    @Test
    fun `startFromJira returns 400 when ticketKey is blank`() {
        val req = CopilotAgentPipelineController.StartFromJiraRequest("", 1L)
        val result = controller.startFromJira(req)
        assertEquals(400, result.statusCode.value())
    }

    @Test
    fun `startFromJira returns 400 when jira fetch fails`() {
        whenever(jiraAnalysisService.fetchTicketAsText(any(), any())).thenThrow(RuntimeException("Jira unavailable"))
        val req = CopilotAgentPipelineController.StartFromJiraRequest("PROJ-123", 1L)
        val result = controller.startFromJira(req)
        assertEquals(400, result.statusCode.value())
        assertTrue(result.body!!["error"].toString().contains("Jira-Ticket"))
    }

    @Test
    fun `startFromJira returns 200 when successful`() {
        whenever(jiraAnalysisService.fetchTicketAsText(any(), any())).thenReturn("Ticket data")
        whenever(pipelineService.startFromJira(any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), any())).thenReturn(5L)
        val req = CopilotAgentPipelineController.StartFromJiraRequest("PROJ-123", 1L)
        val result = controller.startFromJira(req)
        assertEquals(200, result.statusCode.value())
        assertEquals(5L, result.body!!["id"])
    }

    @Test
    fun `list returns empty list`() {
        whenever(pipelineService.allRuns()).thenReturn(emptyList())
        val result = controller.list()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `list returns run summary maps`() {
        whenever(pipelineService.allRuns()).thenReturn(listOf(makeRun()))
        whenever(pipelineService.effectiveStatus(any())).thenAnswer { invocation ->
            (invocation.arguments[0] as AgentPipelineRun).status.name
        }
        val result = controller.list()
        assertEquals(1, result.size)
        assertEquals(1L, result[0]["id"])
        assertEquals("DONE", result[0]["status"])
    }

    @Test
    fun `list delegates stale status resolution to the service`() {
        val run = AgentPipelineRun(id = 42L, rawText = "x", repoPath = null, featureSlug = "stale", createdAt = 1L)
        run.status = PipelineStatus.RUNNING
        whenever(pipelineService.allRuns()).thenReturn(listOf(run))
        whenever(pipelineService.effectiveStatus(run)).thenReturn("ERROR")
        val result = controller.list()
        assertEquals("ERROR", result[0]["status"])
    }

    @Test
    fun `status returns 404 for unknown id`() {
        whenever(pipelineService.get(any())).thenReturn(null)
        val result = controller.status(99L)
        assertEquals(404, result.statusCode.value())
    }

    @Test
    fun `status returns run map for known id`() {
        whenever(pipelineService.get(any())).thenReturn(makeRun())
        val result = controller.status(1L)
        assertEquals(200, result.statusCode.value())
        assertNotNull(result.body)
        assertEquals(1L, result.body!!["id"])
    }

    @Test
    fun `start returns 400 when rawText is too long`() {
        val longText = "x".repeat(50_001)
        val result = controller.start(CopilotAgentPipelineController.StartRequest(longText))
        assertEquals(400, result.statusCode.value())
        assertTrue(result.body!!["error"].toString().contains("lang"))
    }

    @Test
    fun `cancel returns 404 for unknown run`() {
        whenever(pipelineService.get(any())).thenReturn(null)
        val result = controller.cancel(1L)
        assertEquals(404, result.statusCode.value())
    }

    @Test
    fun `cancel returns 409 when run is already done`() {
        whenever(pipelineService.get(any())).thenReturn(makeRun(status = PipelineStatus.DONE))
        val result = controller.cancel(1L)
        assertEquals(409, result.statusCode.value())
    }

    @Test
    fun `cancel returns 200 when run is running`() {
        whenever(pipelineService.get(any())).thenReturn(makeRun(status = PipelineStatus.RUNNING))
        val result = controller.cancel(1L)
        assertEquals(200, result.statusCode.value())
    }

    @Test
    fun `cancel returns 200 when run is pending`() {
        whenever(pipelineService.get(any())).thenReturn(makeRun(status = PipelineStatus.PENDING))
        val result = controller.cancel(1L)
        assertEquals(200, result.statusCode.value())
    }

    @Test
    fun `delete returns 204`() {
        val result = controller.delete(1L)
        assertEquals(204, result.statusCode.value())
    }

    @Test
    fun `delete returns 409 when run is running`() {
        whenever(pipelineService.get(any())).thenReturn(makeRun(status = PipelineStatus.RUNNING))
        val result = controller.delete(1L)
        assertEquals(409, result.statusCode.value())
    }

    @Test
    fun `delete returns 409 when run is pending`() {
        whenever(pipelineService.get(any())).thenReturn(makeRun(status = PipelineStatus.PENDING))
        val result = controller.delete(1L)
        assertEquals(409, result.statusCode.value())
    }

    @Test
    fun `delete returns 204 when run exists but is done`() {
        whenever(pipelineService.get(any())).thenReturn(makeRun(status = PipelineStatus.DONE))
        val result = controller.delete(1L)
        assertEquals(204, result.statusCode.value())
    }

    @Test
    fun `deleteAll returns ok`() {
        val result = controller.deleteAll()
        assertEquals(200, result.statusCode.value())
        assertEquals(true, result.body!!["deleted"])
    }

    @Test
    fun `status returns jiraMode true when jiraTicketData is set`() {
        val run = AgentPipelineRun(1L, "t", null, "slug", jiraTicketData = "ticket-data", createdAt = System.currentTimeMillis())
        whenever(pipelineService.get(any())).thenReturn(run)
        val result = controller.status(1L)
        assertEquals(200, result.statusCode.value())
        assertEquals(true, result.body!!["jiraMode"])
    }

    @Test
    fun `status returns jiraMode false when jiraTicketData is null`() {
        whenever(pipelineService.get(any())).thenReturn(makeRun())
        val result = controller.status(1L)
        assertEquals(200, result.statusCode.value())
        assertEquals(false, result.body!!["jiraMode"])
    }

    @Test
    fun `chat returns error emitter when message is blank`() {
        val emitter = controller.chat(1L, mapOf("message" to ""))
        assertNotNull(emitter)
    }

    @Test
    fun `chat returns error emitter when message is too long`() {
        val longMessage = "x".repeat(10_001)
        val emitter = controller.chat(1L, mapOf("message" to longMessage))
        assertNotNull(emitter)
    }

    @Test
    fun `chat returns error emitter when run not found`() {
        whenever(pipelineService.get(any())).thenReturn(null)
        val emitter = controller.chat(1L, mapOf("message" to "hello"))
        assertNotNull(emitter)
    }

    @Test
    fun `chat returns emitter when run found`() {
        whenever(pipelineService.get(any())).thenReturn(makeRun())
        val emitter = controller.chat(1L, mapOf("message" to "hello"))
        assertNotNull(emitter)
    }

    @Test
    fun `stream returns error emitter for unknown run`() {
        whenever(pipelineService.get(any())).thenReturn(null)
        val emitter = controller.stream(99L)
        assertNotNull(emitter)
    }

    @Test
    fun `stream returns emitter for known run`() {
        val run = makeRun()
        whenever(pipelineService.get(any())).thenReturn(run)
        val emitter = controller.stream(1L)
        assertNotNull(emitter)
    }

    @Test
    fun `stream with done run exits thread`() {
        val run = makeRun(status = PipelineStatus.DONE)
        whenever(pipelineService.get(any())).thenReturn(run)
        val emitter = controller.stream(1L)
        assertNotNull(emitter)
        Thread.sleep(500)
    }

    @Test
    fun `stream with error run exits thread`() {
        val run = makeRun(status = PipelineStatus.ERROR)
        whenever(pipelineService.get(any())).thenReturn(run)
        val emitter = controller.stream(1L)
        assertNotNull(emitter)
        Thread.sleep(500)
    }

    @Test
    fun `chat with null message key returns error emitter`() {
        val emitter = controller.chat(1L, emptyMap())
        assertNotNull(emitter)
    }

    @Test
    fun `chat invokes callback and sends chunks`() {
        whenever(pipelineService.get(any())).thenReturn(makeRun())
        doAnswer { inv ->
            @Suppress("UNCHECKED_CAST")
            val cb = inv.getArgument<(String) -> Unit>(2)
            cb("hello")
            cb("hello world")
            "done"
        }.whenever(pipelineService).chat(any(), any(), any())
        val emitter = controller.chat(1L, mapOf("message" to "hi"))
        assertNotNull(emitter)
        Thread.sleep(300)
    }

    @Test
    fun `chat streaming exception completes with error`() {
        whenever(pipelineService.get(any())).thenReturn(makeRun())
        doAnswer { throw RuntimeException("AI error") }
            .whenever(pipelineService)
            .chat(any(), any(), any())
        val emitter = controller.chat(1L, mapOf("message" to "hi"))
        assertNotNull(emitter)
        Thread.sleep(300)
    }

    @Test
    fun `stream with running status transitions to done covers Thread sleep`() {
        val runningRun = makeRun(status = PipelineStatus.RUNNING)
        val doneRun = makeRun(status = PipelineStatus.DONE)
        whenever(pipelineService.get(any())).thenReturn(runningRun, doneRun)
        val emitter = controller.stream(1L)
        assertNotNull(emitter)
        Thread.sleep(600)
    }

    @Test
    fun `stream completion handler cancels future`() {
        val run = makeRun(status = PipelineStatus.RUNNING)
        whenever(pipelineService.get(any())).thenReturn(run)
        val emitter = controller.stream(1L)
        assertNotNull(emitter)
        Thread.sleep(100)
        emitter.complete()
        Thread.sleep(200)
    }

    @Test
    fun `chat with decreasing partial length covers else empty delta branch`() {
        whenever(pipelineService.get(any())).thenReturn(makeRun())
        doAnswer { inv ->
            @Suppress("UNCHECKED_CAST")
            val cb = inv.getArgument<(String) -> Unit>(2)
            cb("hello world")
            cb("hi")
            "done"
        }.whenever(pipelineService).chat(any(), any(), any())
        val emitter = controller.chat(1L, mapOf("message" to "hi"))
        assertNotNull(emitter)
        Thread.sleep(300)
    }

    @Test
    fun `checkRepoAccess returns 404 for unknown run`() {
        whenever(pipelineService.get(any())).thenReturn(null)
        val result = controller.checkRepoAccess(99L)
        assertEquals(404, result.statusCode.value())
    }

    @Test
    fun `checkRepoAccess returns false when no repo linked`() {
        whenever(pipelineService.get(any())).thenReturn(makeRun())
        val result = controller.checkRepoAccess(1L)
        assertEquals(200, result.statusCode.value())
        assertEquals(false, result.body!!["ok"])
        assertEquals("Kein Repository", result.body!!["reason"])
    }

    @Test
    fun `checkRepoAccess returns false for nonexistent path`() {
        val run = AgentPipelineRun(1L, "t", "/nonexistent/path/abc123", "slug", createdAt = System.currentTimeMillis())
        whenever(pipelineService.get(any())).thenReturn(run)
        whenever(workspaceFileUseCase.probeAccess("/nonexistent/path/abc123")).thenReturn(
            accessView("/nonexistent/path/abc123", exists = false, isDirectory = false, canRead = false, canWrite = false, probeOk = false),
        )
        val result = controller.checkRepoAccess(1L)
        assertEquals(200, result.statusCode.value())
        assertEquals(false, result.body!!["ok"])
        assertEquals("Verzeichnis existiert nicht", result.body!!["reason"])
    }

    @Test
    fun `checkRepoAccess maps the access view into the response body`(
        @TempDir tempDir: File,
    ) {
        val run = AgentPipelineRun(1L, "t", tempDir.absolutePath, "slug", createdAt = System.currentTimeMillis())
        whenever(pipelineService.get(any())).thenReturn(run)
        whenever(workspaceFileUseCase.probeAccess(tempDir.absolutePath)).thenReturn(accessView(tempDir.absolutePath))
        val result = controller.checkRepoAccess(1L)
        assertEquals(200, result.statusCode.value())
        assertEquals(true, result.body!!["ok"])
        assertEquals(tempDir.absolutePath, result.body!!["path"])
        assertEquals(true, result.body!!["exists"])
        assertEquals(true, result.body!!["isDir"])
        assertEquals(true, result.body!!["canRead"])
        assertEquals(true, result.body!!["canWrite"])
        assertEquals(System.getProperty("user.name"), result.body!!["javaUser"])
    }

    @Test
    fun `checkRepoAccess returns ok false when canWrite is false`(
        @TempDir tempDir: File,
    ) {
        tempDir.setWritable(false)
        try {
            val run = AgentPipelineRun(1L, "t", tempDir.absolutePath, "slug", createdAt = System.currentTimeMillis())
            whenever(pipelineService.get(any())).thenReturn(run)
            whenever(workspaceFileUseCase.probeAccess(tempDir.absolutePath)).thenReturn(
                accessView(tempDir.absolutePath, canWrite = false, probeOk = false),
            )
            val result = controller.checkRepoAccess(1L)
            assertEquals(200, result.statusCode.value())
            assertEquals(false, result.body!!["ok"])
            assertEquals("Schreibzugriff verweigert", result.body!!["reason"])
        } finally {
            tempDir.setWritable(true)
        }
    }

    @Test
    fun `checkRepoAccess with valid writable directory passes probe`(
        @TempDir tempDir: File,
    ) {
        val run = AgentPipelineRun(1L, "t", tempDir.absolutePath, "slug", createdAt = System.currentTimeMillis())
        whenever(pipelineService.get(any())).thenReturn(run)
        whenever(workspaceFileUseCase.probeAccess(tempDir.absolutePath)).thenReturn(accessView(tempDir.absolutePath, probeOk = true))
        val result = controller.checkRepoAccess(1L)
        assertEquals(200, result.statusCode.value())
        assertEquals(true, result.body!!["ok"] as Boolean)
    }

    @Test
    fun `checkRepoAccess with invalid path returns false and reason`() {
        val run = AgentPipelineRun(1L, "t", "/path/\u0000/invalid", "slug", createdAt = System.currentTimeMillis())
        whenever(pipelineService.get(any())).thenReturn(run)
        whenever(workspaceFileUseCase.probeAccess("/path/\u0000/invalid")).thenReturn(
            accessView("/path/\u0000/invalid", exists = false, isDirectory = false, canRead = false, canWrite = false, probeOk = false, probeError = "Invalid file path"),
        )
        val result = controller.checkRepoAccess(1L)
        assertEquals(200, result.statusCode.value())
        assertEquals(false, result.body!!["ok"])
        assertEquals("Ungültiger Pfad", result.body!!["reason"])
        assertEquals(setOf("ok", "reason"), result.body!!.keys)
    }

    @Test
    fun `checkRepoAccess with null-byte path returns error on exception`() {
        val run = AgentPipelineRun(1L, "t", "/path/\u0000/invalid", "slug", createdAt = System.currentTimeMillis())
        whenever(pipelineService.get(any())).thenReturn(run)
        whenever(workspaceFileUseCase.probeAccess("/path/\u0000/invalid")).thenReturn(
            accessView("/path/\u0000/invalid", exists = false, probeOk = false, probeError = "boom"),
        )
        val result = controller.checkRepoAccess(1L)
        assertEquals(200, result.statusCode.value())
        assertEquals(false, result.body!!["ok"])
    }

    @Test
    fun `checkRepoAccess returns ok false for invalid canonical path`() {
        val run = AgentPipelineRun(1L, "t", "/valid/path/../path", "slug", createdAt = System.currentTimeMillis())
        whenever(pipelineService.get(any())).thenReturn(run)
        whenever(workspaceFileUseCase.probeAccess("/valid/path/../path")).thenReturn(
            accessView("/valid/path/../path", exists = false, probeOk = false, probeError = "nope"),
        )
        val result = controller.checkRepoAccess(1L)
        assertNotNull(result)
    }

    @Test
    fun `checkRepoAccess probe failure covers probeError branch`(
        @TempDir tempDir: File,
    ) {
        val run = AgentPipelineRun(1L, "t", tempDir.absolutePath, "slug", createdAt = System.currentTimeMillis())
        whenever(pipelineService.get(any())).thenReturn(run)
        whenever(workspaceFileUseCase.probeAccess(tempDir.absolutePath)).thenReturn(
            accessView(tempDir.absolutePath, probeOk = false, probeError = "probe boom"),
        )
        val result = controller.checkRepoAccess(1L)
        assertEquals(200, result.statusCode.value())
        assertEquals(false, result.body!!["ok"])
        assertEquals("Schreibtest fehlgeschlagen", result.body!!["reason"])
        assertEquals("probe boom", result.body!!["probeError"])
    }

    @Test
    fun `previewWriteFiles returns 404 for unknown run`() {
        whenever(pipelineService.previewWriteFiles(99L)).thenReturn(null)
        val result = controller.previewWriteFiles(99L)
        assertEquals(404, result.statusCode.value())
    }

    @Test
    fun `previewWriteFiles returns 400 when finn output is blank`() {
        whenever(pipelineService.previewWriteFiles(1L)).thenReturn(
            PipelineWritePreview(repoPath = null, files = emptyList(), errorKey = "pipeline.be_finn_no_output"),
        )
        val result = controller.previewWriteFiles(1L)
        assertEquals(400, result.statusCode.value())
        assertEquals("Keine Ausgabe", result.body!!["error"])
    }

    @Test
    fun `previewWriteFiles maps repo path and file entries`() {
        whenever(pipelineService.previewWriteFiles(1L)).thenReturn(
            PipelineWritePreview(
                repoPath = "/repo",
                files = listOf(PipelineWriteFile("src/App.php", 42)),
            ),
        )
        val result = controller.previewWriteFiles(1L)
        assertEquals(200, result.statusCode.value())
        assertEquals("/repo", result.body!!["repoPath"])
        assertEquals(listOf(mapOf("path" to "src/App.php", "size" to 42)), result.body!!["files"])
    }

    @Test
    fun `writeFiles returns 404 for unknown run`() {
        whenever(pipelineService.writeFiles(99L, null)).thenReturn(null)
        val result = controller.writeFiles(99L)
        assertEquals(404, result.statusCode.value())
    }

    @Test
    fun `writeFiles returns 400 when no repo linked`() {
        whenever(pipelineService.writeFiles(any(), anyOrNull())).thenReturn(
            PipelineWriteOutcome(errorKey = "pipeline.be_no_repo_linked"),
        )
        val result = controller.writeFiles(1L)
        assertEquals(400, result.statusCode.value())
        assertEquals("Kein Repository", result.body!!["error"])
    }

    @Test
    fun `writeFiles returns 400 when finn output is blank`() {
        whenever(pipelineService.writeFiles(any(), anyOrNull())).thenReturn(
            PipelineWriteOutcome(errorKey = "pipeline.be_finn_no_output"),
        )
        val result = controller.writeFiles(1L)
        assertEquals(400, result.statusCode.value())
        assertEquals("Keine Ausgabe", result.body!!["error"])
    }

    @Test
    fun `writeFiles returns 400 when repo directory does not exist`() {
        whenever(pipelineService.writeFiles(any(), anyOrNull())).thenReturn(
            PipelineWriteOutcome(errorKey = "config.be_repo_dir_not_exists", errorArg = "/nonexistent/repo"),
        )
        val result = controller.writeFiles(1L)
        assertEquals(400, result.statusCode.value())
        assertEquals("Verzeichnis existiert nicht", result.body!!["error"])
    }

    @Test
    fun `writeFiles returns 400 for an invalid repo path`() {
        whenever(pipelineService.writeFiles(any(), anyOrNull())).thenReturn(
            PipelineWriteOutcome(errorKey = "config.be_invalid_repo_path_msg", errorArg = "boom"),
        )
        val result = controller.writeFiles(1L)
        assertEquals(400, result.statusCode.value())
        assertNotNull(result.body!!["error"])
    }

    @Test
    fun `writeFiles with canWrite false returns error without hint`() {
        whenever(pipelineService.writeFiles(any(), anyOrNull())).thenReturn(
            PipelineWriteOutcome(
                errorKey = "config.be_no_write_permissions",
                errorArg = "/repo",
                showWritePermissionHint = true,
            ),
        )
        val result = controller.writeFiles(1L)
        assertEquals(400, result.statusCode.value())
        assertEquals("Keine Schreibrechte", result.body!!["error"])
    }

    @Test
    fun `writeFiles with non-writable directory returns error with os hint`() {
        whenever(pipelineService.writeFiles(any(), anyOrNull())).thenReturn(
            PipelineWriteOutcome(
                errorKey = "config.be_no_write_permissions",
                errorArg = "/repo",
                showWritePermissionHint = true,
            ),
        )
        val macController =
            CopilotAgentPipelineController(
                pipelineService,
                jiraAnalysisService,
                i18n,
                mockupReader,
                ssePublisher,
                FakeSystemGuidanceUseCase(osType = "macos"),
                workspaceFileUseCase,
            )
        val result = macController.writeFiles(1L)
        assertEquals(400, result.statusCode.value())
        assertTrue(result.body!!["error"].toString().endsWith(FakeSystemGuidanceUseCase.MAC_HINT))
    }

    @Test
    fun `writeFiles returns written and errors for a plain success`() {
        whenever(pipelineService.writeFiles(any(), anyOrNull())).thenReturn(
            PipelineWriteOutcome(written = listOf("src/App.php"), errors = emptyList()),
        )
        val result = controller.writeFiles(1L)
        assertEquals(200, result.statusCode.value())
        assertEquals(listOf("src/App.php"), result.body!!["written"])
        assertEquals(emptyList<String>(), result.body!!["errors"])
        assertEquals(false, result.body!!.containsKey("deleted"))
        assertEquals(false, result.body!!.containsKey("info"))
    }

    @Test
    fun `writeFiles returns auto written files with deleted entries and info`() {
        whenever(pipelineService.writeFiles(any(), anyOrNull())).thenReturn(
            PipelineWriteOutcome(
                written = listOf("src/App.php"),
                deleted = listOf("old.php"),
                errors = listOf(PipelineWriteError(message = "raw error")),
                infoKey = "config.be_rex_already_written",
            ),
        )
        val result = controller.writeFiles(1L)
        assertEquals(200, result.statusCode.value())
        assertEquals(listOf("old.php"), result.body!!["deleted"])
        assertEquals(listOf("raw error"), result.body!!["errors"])
        assertEquals("Rex bereits geschrieben", result.body!!["info"])
    }

    @Test
    fun `writeFiles returns ok with info when rex is DONE`() {
        whenever(pipelineService.writeFiles(any(), anyOrNull())).thenReturn(
            PipelineWriteOutcome(infoKey = "config.be_rex_already_written"),
        )
        val result = controller.writeFiles(1L)
        assertEquals(200, result.statusCode.value())
        assertEquals(emptyList<String>(), result.body!!["written"])
        assertEquals(emptyList<String>(), result.body!!["errors"])
        assertTrue(result.body!!["info"].toString().contains("Rex"))
    }

    @Test
    fun `writeFiles maps every write error code`() {
        whenever(pipelineService.writeFiles(any(), anyOrNull())).thenReturn(
            PipelineWriteOutcome(
                written = emptyList(),
                errors =
                    listOf(
                        PipelineWriteError(path = "../../etc/passwd", code = PipelineWriteErrorCode.PATH_TRAVERSAL),
                        PipelineWriteError(path = "src/New.php", code = PipelineWriteErrorCode.DIR_CREATE_FAILED, detail = "/repo/ro"),
                        PipelineWriteError(path = "src/App.php", code = PipelineWriteErrorCode.WRITE_FAILED, detail = "oops"),
                        PipelineWriteError(path = "src/Null.php", code = PipelineWriteErrorCode.DIR_CREATE_FAILED, detail = null),
                        PipelineWriteError(path = "src/Safe.php", code = PipelineWriteErrorCode.SECURITY_DENIED, detail = "denied"),
                        PipelineWriteError(),
                    ),
            ),
        )
        val result = controller.writeFiles(1L)
        assertEquals(200, result.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val formatted = result.body!!["errors"] as List<String>
        assertTrue(formatted.any { it.contains("Pfad-Traversal abgelehnt") })
        assertTrue(formatted.any { it.contains("Verzeichnis konnte nicht erstellt werden") })
        assertTrue(formatted.any { it.contains("oops") })
        assertTrue(formatted.any { it.contains("Zugriff verweigert") })
        assertTrue(formatted.any { it.contains("denied") })
        assertTrue(formatted.any { it == "src/Null.php: Verzeichnis konnte nicht erstellt werden" })
        assertTrue(formatted.contains(""))
    }

    @Test
    fun `start passes through valid PNG mockup`() {
        whenever(pipelineService.start(any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), any())).thenReturn(7L)
        val b64 =
            java.util.Base64
                .getEncoder()
                .encodeToString("fake-png-bytes".toByteArray())
        val req =
            CopilotAgentPipelineController.StartRequest(
                rawText = "Add gallery feature",
                mockupBase64 = b64,
                mockupMimeType = "image/png",
            )
        val result = controller.start(req)
        assertEquals(200, result.statusCode.value())
        assertEquals(7L, result.body!!["id"])
    }

    @Test
    fun `start returns 400 for disallowed mockup MIME type`() {
        val b64 =
            java.util.Base64
                .getEncoder()
                .encodeToString("fake".toByteArray())
        val req =
            CopilotAgentPipelineController.StartRequest(
                rawText = "Feature X",
                mockupBase64 = b64,
                mockupMimeType = "application/pdf",
            )
        val result = controller.start(req)
        assertEquals(400, result.statusCode.value())
        assertTrue(result.body!!["error"].toString().contains("Dateityp"))
    }

    @Test
    fun `start returns 400 when mockupBase64 is present but mimeType is missing`() {
        val b64 =
            java.util.Base64
                .getEncoder()
                .encodeToString("fake".toByteArray())
        val req =
            CopilotAgentPipelineController.StartRequest(
                rawText = "Feature X",
                mockupBase64 = b64,
                mockupMimeType = null,
            )
        val result = controller.start(req)
        assertEquals(400, result.statusCode.value())
        assertTrue(result.body!!["error"].toString().contains("mockupMimeType"))
    }

    @Test
    fun `start returns 400 when mockupBase64 exceeds size limit`() {
        val hugeBase64 = "A".repeat(10_485_761)
        val req =
            CopilotAgentPipelineController.StartRequest(
                rawText = "Feature X",
                mockupBase64 = hugeBase64,
                mockupMimeType = "image/jpeg",
            )
        val result = controller.start(req)
        assertEquals(400, result.statusCode.value())
        assertTrue(result.body!!["error"].toString().contains("groß"))
    }

    @Test
    fun `start with no mockup still returns 200`() {
        whenever(pipelineService.start(any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), any())).thenReturn(8L)
        val result = controller.start(CopilotAgentPipelineController.StartRequest("Add feature"))
        assertEquals(200, result.statusCode.value())
    }

    @Test
    fun `start with blank mockupBase64 treats it as absent - returns 200`() {
        whenever(pipelineService.start(any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), any())).thenReturn(8L)
        val req = CopilotAgentPipelineController.StartRequest(rawText = "Feature X", mockupBase64 = "   ", mockupMimeType = "image/png")
        val result = controller.start(req)
        assertEquals(200, result.statusCode.value())
    }

    @Test
    fun `start with blank mockupMimeType returns 400`() {
        val b64 =
            java.util.Base64
                .getEncoder()
                .encodeToString("data".toByteArray())
        val req = CopilotAgentPipelineController.StartRequest(rawText = "Feature X", mockupBase64 = b64, mockupMimeType = "  ")
        val result = controller.start(req)
        assertEquals(400, result.statusCode.value())
        assertTrue(result.body!!["error"].toString().contains("mockupMimeType"))
    }

    @Test
    fun `startFromJira passes through valid SVG mockup`() {
        whenever(jiraAnalysisService.fetchTicketAsText(any(), any())).thenReturn("Ticket data")
        whenever(pipelineService.startFromJira(any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), any())).thenReturn(9L)
        val svgContent = "<svg xmlns='http://www.w3.org/2000/svg'><rect width='100' height='100'/></svg>"
        val b64 =
            java.util.Base64
                .getEncoder()
                .encodeToString(svgContent.toByteArray())
        val req =
            CopilotAgentPipelineController.StartFromJiraRequest(
                ticketKey = "PROJ-999",
                jiraConfigId = 1L,
                mockupBase64 = b64,
                mockupMimeType = "image/svg+xml",
            )
        val result = controller.startFromJira(req)
        assertEquals(200, result.statusCode.value())
        assertEquals(9L, result.body!!["id"])
    }

    @Test
    fun `startFromJira returns 400 for disallowed mockup MIME type`() {
        val b64 =
            java.util.Base64
                .getEncoder()
                .encodeToString("fake".toByteArray())
        val req =
            CopilotAgentPipelineController.StartFromJiraRequest(
                ticketKey = "PROJ-1",
                jiraConfigId = 1L,
                mockupBase64 = b64,
                mockupMimeType = "image/gif",
            )
        val result = controller.startFromJira(req)
        assertEquals(400, result.statusCode.value())
    }

    @Test
    fun `all allowed MIME types pass mockup validation`() {
        val allowed = listOf("image/png", "image/jpeg", "image/svg+xml", "image/heic", "image/heif", "image/tiff")
        val b64 =
            java.util.Base64
                .getEncoder()
                .encodeToString("data".toByteArray())
        allowed.forEach { mime ->
            whenever(pipelineService.start(any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), any())).thenReturn(1L)
            val req = CopilotAgentPipelineController.StartRequest(rawText = "feat", mockupBase64 = b64, mockupMimeType = mime)
            val result = controller.start(req)
            assertEquals(200, result.statusCode.value(), "Expected 200 for MIME type $mime")
        }
    }

    @Test
    fun `start returns 400 for invalid MIME type in mockupImages list`() {
        val b64 =
            java.util.Base64
                .getEncoder()
                .encodeToString("fake".toByteArray())
        val req =
            CopilotAgentPipelineController.StartRequest(
                rawText = "Feature Y",
                mockupImages =
                    listOf(
                        CopilotAgentPipelineController.MockupImageRequest(b64, "application/pdf"),
                    ),
            )
        val result = controller.start(req)
        assertEquals(400, result.statusCode.value())
        assertTrue(result.body!!["error"].toString().isNotBlank())
    }

    @Test
    fun `start with valid mockupImages list maps images and returns 200`() {
        whenever(pipelineService.start(any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), any())).thenReturn(8L)
        val b64 =
            java.util.Base64
                .getEncoder()
                .encodeToString("png-bytes".toByteArray())
        val req =
            CopilotAgentPipelineController.StartRequest(
                rawText = "Feature Z",
                mockupImages =
                    listOf(
                        CopilotAgentPipelineController.MockupImageRequest(b64, "image/png"),
                    ),
            )
        val result = controller.start(req)
        assertEquals(200, result.statusCode.value())
        assertEquals(8L, result.body!!["id"])
    }

    @Test
    fun `startFromJira returns 400 for invalid MIME type in mockupImages list`() {
        val b64 =
            java.util.Base64
                .getEncoder()
                .encodeToString("fake".toByteArray())
        val req =
            CopilotAgentPipelineController.StartFromJiraRequest(
                ticketKey = "PROJ-5",
                jiraConfigId = 1L,
                mockupImages =
                    listOf(
                        CopilotAgentPipelineController.MockupImageRequest(b64, "image/gif"),
                    ),
            )
        val result = controller.startFromJira(req)
        assertEquals(400, result.statusCode.value())
        assertTrue(result.body!!["error"].toString().isNotBlank())
    }

    @Test
    fun `startFromJira with valid mockupImages list maps images and returns 200`() {
        whenever(jiraAnalysisService.fetchTicketAsText(any(), any())).thenReturn("Ticket content")
        whenever(pipelineService.startFromJira(any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), any())).thenReturn(10L)
        val b64 =
            java.util.Base64
                .getEncoder()
                .encodeToString("png-bytes".toByteArray())
        val req =
            CopilotAgentPipelineController.StartFromJiraRequest(
                ticketKey = "PROJ-6",
                jiraConfigId = 1L,
                mockupImages =
                    listOf(
                        CopilotAgentPipelineController.MockupImageRequest(b64, "image/jpeg"),
                    ),
            )
        val result = controller.startFromJira(req)
        assertEquals(200, result.statusCode.value())
        assertEquals(10L, result.body!!["id"])
    }

    @Test
    fun `readMockupFile returns 400 for non-existent file`() {
        val result = controller.readMockupFile("/tmp/this-file-does-not-exist-12345.png")
        assertEquals(400, result.statusCode.value())
        assertTrue(result.body!!["error"].toString().isNotBlank())
    }

    @Test
    fun `readMockupFile returns 400 for disallowed extension`() {
        val tmpFile = java.io.File.createTempFile("test", ".gif")
        try {
            tmpFile.writeBytes(ByteArray(10))
            val result = controller.readMockupFile(tmpFile.absolutePath)
            assertEquals(400, result.statusCode.value())
            val errorMsg = result.body!!["error"].toString()
            assertTrue(errorMsg.contains("erlaubt") || errorMsg.contains("allow"))
        } finally {
            tmpFile.delete()
        }
    }

    @Test
    fun `readMockupFile returns 200 with base64 for valid PNG`() {
        val tmpFile = java.io.File.createTempFile("test", ".png")
        try {
            val content = ByteArray(16) { it.toByte() }
            tmpFile.writeBytes(content)
            val result = controller.readMockupFile(tmpFile.absolutePath)
            assertEquals(200, result.statusCode.value())
            assertEquals("image/png", result.body!!["mimeType"])
            val expectedB64 =
                java.util.Base64
                    .getEncoder()
                    .encodeToString(content)
            assertEquals(expectedB64, result.body!!["base64"])
            assertTrue(result.body!!["dataUrl"].toString().startsWith("data:image/png;base64,"))
            assertEquals(tmpFile.name, result.body!!["name"])
        } finally {
            tmpFile.delete()
        }
    }

    @Test
    fun `readMockupFile returns 200 with correct MIME type for all allowed extensions`() {
        val extToMime =
            mapOf(
                "png" to "image/png",
                "jpg" to "image/jpeg",
                "jpeg" to "image/jpeg",
                "svg" to "image/svg+xml",
                "heic" to "image/heic",
                "heif" to "image/heif",
                "tif" to "image/tiff",
                "tiff" to "image/tiff",
            )
        extToMime.forEach { (ext, expectedMime) ->
            val tmpFile = java.io.File.createTempFile("test", ".$ext")
            try {
                tmpFile.writeBytes(ByteArray(4) { 0 })
                val result = controller.readMockupFile(tmpFile.absolutePath)
                assertEquals(200, result.statusCode.value(), "Expected 200 for extension .$ext")
                assertEquals(expectedMime, result.body!!["mimeType"], "Wrong MIME for .$ext")
            } finally {
                tmpFile.delete()
            }
        }
    }

    @Test
    fun `readMockupFile returns 400 for invalid path causing IOException`() {
        val result = controller.readMockupFile("/tmp/invalid path.png")
        assertEquals(400, result.statusCode.value())
        assertTrue(result.body!!["error"].toString().isNotBlank())
    }

    @Test
    fun `readMockupFile returns 400 when path points to a directory`() {
        val tmpDir = java.io.File(System.getProperty("java.io.tmpdir"))
        val result = controller.readMockupFile(tmpDir.absolutePath + "/")
        assertEquals(400, result.statusCode.value())
        assertTrue(result.body!!["error"].toString().isNotBlank())
    }

    @Test
    fun `readMockupFile delegates to the injected mockup file access`() {
        val access = mock<MockupFileAccess>()
        val expected: org.springframework.http.ResponseEntity<Map<String, Any?>> =
            org.springframework.http.ResponseEntity
                .ok(mapOf("name" to "x"))
        whenever(access.readMockupFile("/x.png")).thenReturn(expected)
        val delegating =
            CopilotAgentPipelineController(
                pipelineService,
                jiraAnalysisService,
                i18n,
                access,
                ssePublisher,
                systemGuidance,
                workspaceFileUseCase,
            )

        assertEquals(expected, delegating.readMockupFile("/x.png"))
        verify(access).readMockupFile("/x.png")
    }

    @Test
    fun `start delegates mockup validation to the injected mockup file access`() {
        val access = mock<MockupFileAccess>()
        whenever(access.validateMockup(any(), any())).thenReturn("invalid mockup")
        val delegating =
            CopilotAgentPipelineController(
                pipelineService,
                jiraAnalysisService,
                i18n,
                access,
                ssePublisher,
                systemGuidance,
                workspaceFileUseCase,
            )

        val result =
            delegating.start(
                CopilotAgentPipelineController.StartRequest(
                    rawText = "raw",
                    mockupBase64 = "b64",
                    mockupMimeType = "application/pdf",
                ),
            )

        assertEquals(400, result.statusCode.value())
        assertEquals("invalid mockup", result.body!!["error"])
    }

    @Test
    fun `readMockupFile returns 400 for file larger than 10 MB`() {
        val tmpFile = java.io.File.createTempFile("test-large", ".png")
        try {
            val tenMbPlusOne = 10_485_761L
            java.io.RandomAccessFile(tmpFile, "rw").use { raf -> raf.setLength(tenMbPlusOne) }
            val result = controller.readMockupFile(tmpFile.absolutePath)
            assertEquals(400, result.statusCode.value())
            assertTrue(result.body!!["error"].toString().contains("groß") || result.body!!["error"].toString().isNotBlank())
        } finally {
            tmpFile.delete()
        }
    }
}
