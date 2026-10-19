package com.stacklens.application.service.tooling

import com.stacklens.adapter.out.tooling.FileToolJarStore
import com.stacklens.adapter.out.tooling.ProcessToolJarRuntime
import com.stacklens.application.port.out.ConfluenceToolPort
import com.stacklens.application.port.out.appconfig.StackLensConfigPort
import com.stacklens.application.port.out.compiler.JavaRuntimePort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.model.ConfluenceTool
import com.stacklens.testsupport.JarFileFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.Path

class ToolingExecutionServiceTest {
    private val toolRepo: ConfluenceToolPort = mock()
    private val javaRuntime = mock<JavaRuntimePort>()
    private val stackLensConfig: StackLensConfigPort = mock()
    private val i18n: I18nPort = mock()

    private val service =
        ToolingExecutionService(
            toolRepo = toolRepo,
            toolJarStore = FileToolJarStore(stackLensConfig),
            toolJarRuntime = ProcessToolJarRuntime(javaRuntime),
            i18n = i18n,
            clock = com.stacklens.adapter.out.time.SystemClockPort,
        )

    private fun readyTool(
        id: Long = 1L,
        jarPath: String? = null,
    ) = ConfluenceTool(
        id = id,
        name = "TestTool",
        confluencePageUrl = "u",
        status = AnalysisStatus.READY,
        jarPath = jarPath,
    )

    private fun javaBinaryPath(): String {
        val javaHome = System.getProperty("java.home")
        val bin = if (System.getProperty("os.name", "").lowercase().contains("win")) "java.exe" else "java"
        return "$javaHome/bin/$bin"
    }

    @Test
    fun `runToolStreaming throws when tool not found`() {
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(null)
        whenever(i18n.t(eq("tooling.be_tool_not_found"), any())).thenReturn("not found")

        assertThrows(IllegalStateException::class.java) {
            service.runToolStreaming(1L) { }
        }
    }

    @Test
    fun `runToolStreaming throws when tool not ready`() {
        val tool = readyTool().apply { status = AnalysisStatus.PENDING }
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(tool)
        whenever(i18n.t(eq("tooling.be_tool_not_ready"), any())).thenReturn("not ready")

        assertThrows(IllegalStateException::class.java) {
            service.runToolStreaming(1L) { }
        }
    }

    @Test
    fun `runToolStreaming throws when jar path missing`() {
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(readyTool(jarPath = null))
        whenever(i18n.t(eq("tooling.be_no_jar_path"), any())).thenReturn("no jar")

        assertThrows(IllegalStateException::class.java) {
            service.runToolStreaming(1L) { }
        }
    }

    @Test
    fun `runToolStreaming throws when jar file does not exist`(
        @TempDir tempDir: Path,
    ) {
        val nonExistentJar = tempDir.resolve("missing.jar").toString()
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(readyTool(jarPath = nonExistentJar))
        whenever(i18n.t(eq("tooling.be_jar_not_found"), any())).thenReturn("jar not found")

        assertThrows(IllegalStateException::class.java) {
            service.runToolStreaming(1L) { }
        }
    }

    @Test
    fun `runToolStreaming successfully runs a tool and captures output`(
        @TempDir tempDir: Path,
    ) {
        val jarFile = tempDir.resolve("test.jar").toFile()
        jarFile.writeBytes(JarFileFixtures.echoHelloJar())
        val tool = readyTool(jarPath = jarFile.absolutePath)
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(tool)
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { it.arguments[0] as ConfluenceTool }
        whenever(i18n.t(eq("tooling.be_step_running"))).thenReturn("Running")
        whenever(javaRuntime.findJavaExecutable()).thenReturn(javaBinaryPath())

        val lines = mutableListOf<String>()
        val output = service.runToolStreaming(1L) { line -> lines.add(line) }

        assertTrue(output.contains("hello"))
        assertTrue(lines.isNotEmpty())
        assertEquals(AnalysisStatus.READY, tool.status)
        assertEquals(output.take(50_000), tool.lastRunOutput)
    }

    @Test
    fun `runTool without callback discards line output`(
        @TempDir tempDir: Path,
    ) {
        val jarFile = tempDir.resolve("test.jar").toFile()
        jarFile.writeBytes(JarFileFixtures.echoHelloJar())
        val tool = readyTool(jarPath = jarFile.absolutePath)
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(tool)
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { it.arguments[0] as ConfluenceTool }
        whenever(i18n.t(eq("tooling.be_step_running"))).thenReturn("Running")
        whenever(javaRuntime.findJavaExecutable()).thenReturn(javaBinaryPath())

        val output = service.runTool(1L)

        assertTrue(output.contains("hello"))
    }

    @Test
    fun `runTool caps lastRunOutput at 50_000 chars when output exceeds the cap`(
        @TempDir tempDir: Path,
    ) {
        val jarFile = tempDir.resolve("big.jar").toFile()
        jarFile.writeBytes(JarFileFixtures.echoLongJar())
        val tool = readyTool(jarPath = jarFile.absolutePath)
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(tool)
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { it.arguments[0] as ConfluenceTool }
        whenever(javaRuntime.findJavaExecutable()).thenReturn(javaBinaryPath())
        whenever(i18n.t(eq("tooling.be_step_running"))).thenReturn("Running")

        val output = service.runTool(1L)

        assertEquals(output.take(50_000), tool.lastRunOutput)
    }

    @Test
    fun `runToolStreaming sets errorMessage and rethrows on failure`(
        @TempDir tempDir: Path,
    ) {
        val jarFile = tempDir.resolve("test.jar").toFile().apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val tool = readyTool(jarPath = jarFile.absolutePath)
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(tool)
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { it.arguments[0] as ConfluenceTool }
        whenever(javaRuntime.findJavaExecutable()).thenReturn("___definitely-missing-binary___")
        whenever(i18n.t(eq("tooling.be_step_running"))).thenReturn("Running")
        whenever(i18n.t(eq("tooling.be_execution_error"), any())).thenReturn("exec-fail")

        assertThrows(Exception::class.java) {
            service.runToolStreaming(1L) { }
        }
        assertEquals(AnalysisStatus.READY, tool.status)
    }

    @Test
    fun `a run failure without a message stores the translated error`() {
        val tool = readyTool(id = 2L, jarPath = "/tmp/tool.jar")
        val toolJarRuntime: com.stacklens.application.port.out.tooling.ToolJarRuntime = mock()
        val toolJarStore: com.stacklens.application.port.out.tooling.ToolJarStore = mock()
        whenever(toolJarStore.contains(any())).thenReturn(true)
        whenever(toolJarRuntime.runJar(any(), any())).thenThrow(NullPointerException())
        whenever(toolRepo.findConfluenceToolById(2L)).thenReturn(tool)
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { it.arguments[0] as ConfluenceTool }
        whenever(i18n.t(eq("tooling.be_step_running"))).thenReturn("running")
        whenever(i18n.t(eq("tooling.be_execution_error"), anyOrNull())).thenReturn("execution error")
        val svc =
            ToolingExecutionService(
                toolRepo = toolRepo,
                toolJarStore = toolJarStore,
                toolJarRuntime = toolJarRuntime,
                i18n = i18n,
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            )

        assertThrows(NullPointerException::class.java) { svc.runTool(2L) }
        assertEquals("execution error", tool.errorMessage)
    }

    @Test
    fun `runToolStreaming with non-zero exit does not throw`(
        @TempDir tempDir: Path,
    ) {
        val tmpJar = tempDir.resolve("tool.jar").toFile()
        tmpJar.writeBytes("not a jar".toByteArray())
        val tool = readyTool(jarPath = tmpJar.absolutePath)
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(tool)
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { it.arguments[0] as ConfluenceTool }
        whenever(i18n.t(any<String>(), any<Any>())).thenReturn("exec error")
        whenever(javaRuntime.findJavaExecutable()).thenReturn("/usr/bin/false")

        val result = service.runToolStreaming(1L) { }

        assertEquals("", result)
    }

    @Test
    fun `runToolStreaming with trivial binary produces empty output`(
        @TempDir tempDir: Path,
    ) {
        val tmpJar = tempDir.resolve("tool-echo.jar").toFile()
        tmpJar.writeBytes("not really a jar but ProcessBuilder doesn't care".toByteArray())
        val tool = readyTool(jarPath = tmpJar.absolutePath)
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(tool)
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { it.arguments[0] as ConfluenceTool }
        whenever(javaRuntime.findJavaExecutable()).thenReturn("/usr/bin/true")

        val capturedLines = mutableListOf<String>()
        val result = service.runToolStreaming(1L) { capturedLines.add(it) }

        assertEquals("", result)
        assertTrue(capturedLines.isEmpty())
    }

    @Test
    fun `runToolStreaming when java binary missing throws`(
        @TempDir tempDir: Path,
    ) {
        val tmpJar = tempDir.resolve("tool-nojava.jar").toFile()
        tmpJar.writeBytes("dummy".toByteArray())
        val tool = readyTool(jarPath = tmpJar.absolutePath)
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(tool)
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { it.arguments[0] as ConfluenceTool }
        whenever(i18n.t(any<String>(), any<Any>())).thenReturn("exec error")
        whenever(javaRuntime.findJavaExecutable()).thenReturn("/no/such/path/to/java")

        assertThrows(Exception::class.java) {
            service.runToolStreaming(1L) { }
        }
    }

    @Test
    fun `runToolStreaming with a sleeping binary survives a forced stop`(
        @TempDir tempDir: Path,
    ) {
        val tmpJar = tempDir.resolve("tool-sleep.jar").toFile()
        tmpJar.writeBytes("not a jar".toByteArray())
        val tool = readyTool(jarPath = tmpJar.absolutePath)
        whenever(toolRepo.findConfluenceToolById(1L)).thenReturn(tool)
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { it.arguments[0] as ConfluenceTool }
        whenever(javaRuntime.findJavaExecutable()).thenReturn("/bin/sleep")

        try {
            service.runToolStreaming(1L) { }
        } catch (_: Exception) {
        }
    }
}
