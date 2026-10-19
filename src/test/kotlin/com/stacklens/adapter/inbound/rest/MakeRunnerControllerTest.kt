package com.stacklens.adapter.inbound.rest

import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.application.port.inbound.platform.MakeRunResult
import com.stacklens.application.port.inbound.platform.MakeRunnerUseCase
import com.stacklens.application.port.out.process.ProcessPort
import com.stacklens.application.service.MakeRunnerService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.File
import java.io.IOException
import java.nio.file.Path

class MakeRunnerControllerTest {
    private val processPort: ProcessPort = mock()
    private val service = MakeRunnerService(processPort, JavaFileSystemAdapter())
    private val controller = MakeRunnerController(service)

    @TempDir
    private lateinit var tempDir: Path

    private fun awaitCompleted(emitter: SseEmitter) {
        val deadline = System.currentTimeMillis() + 5_000L
        while (System.currentTimeMillis() < deadline) {
            if (ReflectionTestUtils.getField(emitter, "complete") == true) return
            Thread.sleep(5)
        }
        throw AssertionError("the SSE emitter never completed")
    }

    @Test
    fun `RunRequest defaults`() {
        val req = MakeRunnerController.RunRequest()
        assertEquals("", req.repoPath)
        assertEquals("test", req.target)
    }

    @Test
    fun `RunRequest with values`() {
        val req = MakeRunnerController.RunRequest(repoPath = "/some/path", target = "lint")
        assertEquals("/some/path", req.repoPath)
        assertEquals("lint", req.target)
    }

    @Test
    fun `run returns SseEmitter for non-existent directory`() {
        val req = MakeRunnerController.RunRequest(repoPath = "/nonexistent/dir/xyz123", target = "test")
        val emitter = controller.run(req)
        assertNotNull(emitter)
        awaitCompleted(emitter)
    }

    @Test
    fun `run returns SseEmitter for disallowed target`() {
        val repo = tempDir.toFile()
        File(repo, "Makefile").writeText("test:\n\techo hello\n")
        val req = MakeRunnerController.RunRequest(repoPath = repo.absolutePath, target = "rm-rf-everything")
        val emitter = controller.run(req)
        assertNotNull(emitter)
        awaitCompleted(emitter)
    }

    @Test
    fun `run returns SseEmitter for directory without Makefile`() {
        val req = MakeRunnerController.RunRequest(repoPath = tempDir.toString(), target = "test")
        val emitter = controller.run(req)
        assertNotNull(emitter)
        awaitCompleted(emitter)
    }

    @Test
    fun `run returns SseEmitter for valid directory and allowed target`() {
        val repo = tempDir.toFile()
        File(repo, "Makefile").writeText("test:\n\t@echo hello from make\n")
        val req = MakeRunnerController.RunRequest(repoPath = repo.absolutePath, target = "test")
        val emitter = controller.run(req)
        assertNotNull(emitter)
        awaitCompleted(emitter)
    }

    @Test
    fun `run with blank target defaults to test`() {
        val req = MakeRunnerController.RunRequest(repoPath = "/nonexistent/dir", target = "  ")
        val emitter = controller.run(req)
        assertNotNull(emitter)
        awaitCompleted(emitter)
    }

    @Test
    fun `allowed targets contains expected values`() {
        val allowedTargets = listOf("test", "lint", "build", "coverage", "ci", "fmt", "format")
        val repo = tempDir.toFile()
        File(repo, "Makefile").writeText(
            allowedTargets.joinToString("\n") { target ->
                "$target:\n\t@echo $target\n"
            },
        )
        val emitters =
            allowedTargets.map { target ->
                controller.run(MakeRunnerController.RunRequest(repoPath = repo.absolutePath, target = target)).also { assertNotNull(it) }
            }
        emitters.forEach { awaitCompleted(it) }
    }

    @Test
    fun `run with make that exits nonzero sends fail event`() {
        val repo = tempDir.toFile()
        File(repo, "Makefile").writeText("test:\n\t@exit 1\n")
        val req = MakeRunnerController.RunRequest(repoPath = repo.absolutePath, target = "test")
        val emitter = controller.run(req)
        assertNotNull(emitter)
        awaitCompleted(emitter)
    }

    @Test
    fun `run with null-byte repoPath triggers outer exception catch`() {
        val req = MakeRunnerController.RunRequest(repoPath = "/path/\u0000/invalid", target = "test")
        val emitter = controller.run(req)
        assertNotNull(emitter)
        awaitCompleted(emitter)
    }

    @Test
    fun `run with make outputting lines and early emitter completion triggers inner exception`(
        @TempDir tempDir2: File,
    ) {
        File(tempDir2, "Makefile").writeText("test:\n\t@for i in 1 2 3 4 5 6 7 8; do echo line; sleep 0.05; done\n")
        val req = MakeRunnerController.RunRequest(repoPath = tempDir2.absolutePath, target = "test")
        val emitter = controller.run(req)
        Thread.sleep(200)
        emitter.complete()
        Thread.sleep(600)
    }

    @Test
    fun `run with make outputting lines succeeds with exit 0`(
        @TempDir tempDir2: File,
    ) {
        File(tempDir2, "Makefile").writeText("test:\n\t@echo hello world\n\t@echo second line\n")
        val req = MakeRunnerController.RunRequest(repoPath = tempDir2.absolutePath, target = "test")
        val emitter = controller.run(req)
        assertNotNull(emitter)
        awaitCompleted(emitter)
    }

    @Test
    fun `run with timed-out use case result sends timeout event`(
        @TempDir tempDir2: File,
    ) {
        val useCase = mock<MakeRunnerUseCase>()
        whenever(useCase.run(any(), any(), any())).thenReturn(MakeRunResult(exitCode = -1, timedOut = true))
        val timeoutController = MakeRunnerController(useCase)
        val req = MakeRunnerController.RunRequest(repoPath = tempDir2.absolutePath, target = "test")
        val emitter = timeoutController.run(req)
        assertNotNull(emitter)
        awaitCompleted(emitter)
    }

    @Test
    fun `run emitter send throws in line loop triggers inner exception catch`(
        @TempDir tempDir2: File,
    ) {
        File(tempDir2, "Makefile").writeText("test:\n\t@echo hello\n\t@echo world\n")
        val mockEmitter = Mockito.mock(SseEmitter::class.java)
        doNothing()
            .doNothing()
            .doThrow(IOException("client disconnected"))
            .whenever(mockEmitter)
            .send(any<SseEmitter.SseEventBuilder>())
        val ctrl =
            object : MakeRunnerController(MakeRunnerService(processPort, JavaFileSystemAdapter())) {
                override fun createEmitter() = mockEmitter
            }
        ctrl.run(MakeRunnerController.RunRequest(repoPath = tempDir2.absolutePath, target = "test"))
        verify(mockEmitter, timeout(5_000)).complete()
    }

    @Test
    fun `run outer exception catch when emitter send throws on first line`(
        @TempDir tempDir2: File,
    ) {
        File(tempDir2, "Makefile").writeText("test:\n\t@echo hello\n")
        val mockEmitter = Mockito.mock(SseEmitter::class.java)
        doThrow(IOException("emitter dead"))
            .doNothing()
            .doNothing()
            .whenever(mockEmitter)
            .send(any<SseEmitter.SseEventBuilder>())
        val ctrl =
            object : MakeRunnerController(MakeRunnerService(processPort, JavaFileSystemAdapter())) {
                override fun createEmitter() = mockEmitter
            }
        ctrl.run(MakeRunnerController.RunRequest(repoPath = tempDir2.absolutePath, target = "test"))
        verify(mockEmitter, timeout(5_000)).complete()
    }

    @Test
    fun `run invokes SSE emitter send when use case emits lines via callback`(
        @TempDir tempDir2: File,
    ) {
        val useCase = mock<MakeRunnerUseCase>()
        Mockito
            .doAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                val callback = invocation.getArgument<(String) -> Unit>(2)
                callback("line one")
                callback("line two")
                MakeRunResult(exitCode = 0, timedOut = false)
            }.whenever(useCase)
            .run(any(), any(), any<(String) -> Unit>())

        val ctrl = MakeRunnerController(useCase)
        val emitter = ctrl.run(MakeRunnerController.RunRequest(repoPath = tempDir2.absolutePath, target = "test"))
        assertNotNull(emitter)
        awaitCompleted(emitter)
    }

    @Test
    fun `run sends fail status when make target returns non-zero exit code`(
        @TempDir tempDir2: File,
    ) {
        val useCase = mock<MakeRunnerUseCase>()
        Mockito
            .doAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                val callback = invocation.getArgument<(String) -> Unit>(2)
                callback("error output")
                MakeRunResult(exitCode = 1, timedOut = false)
            }.whenever(useCase)
            .run(any(), any(), any<(String) -> Unit>())

        val ctrl = MakeRunnerController(useCase)
        val emitter = ctrl.run(MakeRunnerController.RunRequest(repoPath = tempDir2.absolutePath, target = "test"))
        assertNotNull(emitter)
        awaitCompleted(emitter)
    }
}
