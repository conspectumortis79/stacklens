package com.stacklens.adapter.out.tooling

import com.stacklens.application.port.out.compiler.JavaRuntimePort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.stream.Stream

class ProcessToolJarRuntimeTest {
    private val javaRuntime = mock<JavaRuntimePort>()

    @Test
    fun `forwards every output line and returns the combined output`(
        @org.junit.jupiter.api.io.TempDir tmpRoot: Path,
    ) {
        whenever(javaRuntime.findJavaExecutable()).thenReturn(javaBinaryPath())
        val jar =
            buildJarInProcess(
                tmpRoot = tmpRoot.toFile(),
                className = "Echo",
                sourceCode =
                    """
                    public class Echo {
                        public static void main(String[] args) {
                            System.out.println("first");
                            System.out.println("second");
                        }
                    }
                    """.trimIndent(),
            ) ?: return
        val runtime = ProcessToolJarRuntime(javaRuntime)
        val lines = mutableListOf<String>()
        val output = runtime.runJar(jar.absolutePath) { lines.add(it) }
        assertTrue(lines.contains("first"), "expected 'first' in $lines")
        assertTrue(lines.contains("second"), "expected 'second' in $lines")
        assertTrue(output.contains("first"))
        assertTrue(output.contains("second"))
    }

    @Test
    fun `runs the jar executable with the java launcher resolved from the compiler port`() {
        whenever(javaRuntime.findJavaExecutable()).thenReturn("/usr/bin/true")
        var capturedCommand: List<String>? = null
        val runtime =
            ProcessToolJarRuntime(javaRuntime, processStarter = { command ->
                capturedCommand = command
                FakeProcess(exitCode = 0, output = "")
            })
        assertEquals("", runtime.runJar("/tmp/tool.jar") { })
        assertEquals(listOf("/usr/bin/true", "-jar", "/tmp/tool.jar"), capturedCommand)
    }

    @Test
    fun `returns empty output when the process prints nothing`(
        @org.junit.jupiter.api.io.TempDir tmpRoot: Path,
    ) {
        val jar = Files.createTempFile(tmpRoot, "silent-", ".jar").toFile()
        whenever(javaRuntime.findJavaExecutable()).thenReturn("/usr/bin/true")
        val runtime =
            ProcessToolJarRuntime(javaRuntime, processStarter = { FakeProcess(exitCode = 0, output = "") })
        assertEquals("", runtime.runJar(jar.absolutePath) { })
    }

    @Test
    fun `kills the process when the run exceeds the timeout`() {
        val process = FakeProcess(exitCode = 0, output = "", waitReturnsFalse = true)
        whenever(javaRuntime.findJavaExecutable()).thenReturn("java")
        val runtime =
            ProcessToolJarRuntime(
                javaRuntime,
                processStarter = { process },
                runTimeoutSeconds = 1L,
            )
        assertEquals("", runtime.runJar("/tmp/tool.jar") { })
        assertTrue(process.destroyed)
    }

    @Test
    fun `destroys a living process when line reading throws`() {
        val process = FakeProcess(exitCode = 0, output = "line\n", isAlive = true)
        whenever(javaRuntime.findJavaExecutable()).thenReturn("java")
        val runtime = ProcessToolJarRuntime(javaRuntime, processStarter = { process })
        val ex =
            assertThrows(RuntimeException::class.java) {
                runtime.runJar("/tmp/tool.jar") { throw RuntimeException("boom") }
            }
        assertEquals("boom", ex.message)
        assertTrue(process.destroyed)
    }

    @Test
    fun `does not destroy an already finished process when reading throws`() {
        val process = FakeProcess(exitCode = 0, output = "line\n", isAlive = false)
        whenever(javaRuntime.findJavaExecutable()).thenReturn("java")
        val runtime = ProcessToolJarRuntime(javaRuntime, processStarter = { process })
        assertThrows(RuntimeException::class.java) {
            runtime.runJar("/tmp/tool.jar") { throw RuntimeException("boom") }
        }
        assertFalse(process.destroyed)
    }

    @Test
    fun `default timeout is long enough for generated tools`() = assertEquals(300L, ProcessToolJarRuntime.DEFAULT_RUN_TIMEOUT_SECONDS)

    private fun javaBinaryPath(): String {
        val javaHome = System.getProperty("java.home")
        val bin = if (System.getProperty("os.name", "").lowercase().contains("win")) "java.exe" else "java"
        return "$javaHome/bin/$bin"
    }

    private fun buildJarInProcess(
        tmpRoot: File,
        className: String,
        sourceCode: String,
    ): File? {
        tmpRoot.mkdirs()
        val src = File(tmpRoot, "$className.java").apply { writeText(sourceCode) }
        val javaCompiler = javax.tools.ToolProvider.getSystemJavaCompiler() ?: return null
        val fileManager = javaCompiler.getStandardFileManager(null, null, null)
        val units = fileManager.getJavaFileObjects(src)
        if (javaCompiler.getTask(null, fileManager, null, null, null, units).call() != true) return null
        val classFile = File(tmpRoot, "$className.class")
        if (!classFile.exists()) return null
        val jar = File(tmpRoot, "$className.jar")
        val manifest =
            Manifest().apply {
                mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
                mainAttributes[Attributes.Name.MAIN_CLASS] = className
            }
        JarOutputStream(FileOutputStream(jar), manifest).use { jos ->
            jos.putNextEntry(JarEntry("$className.class"))
            classFile.inputStream().use { it.copyTo(jos) }
            jos.closeEntry()
        }
        classFile.delete()
        return jar
    }
}

private class FakeProcess(
    private val exitCode: Int,
    private val output: String,
    private val waitReturnsFalse: Boolean = false,
    private val isAlive: Boolean = false,
) : Process() {
    var destroyed: Boolean = false
        private set

    override fun getOutputStream(): OutputStream = ByteArrayOutputStream()

    override fun getInputStream() = ByteArrayInputStream(output.toByteArray())

    override fun getErrorStream() = ByteArrayInputStream(ByteArray(0))

    override fun waitFor(): Int = exitCode

    override fun waitFor(
        timeout: Long,
        unit: TimeUnit,
    ): Boolean = !waitReturnsFalse

    override fun exitValue(): Int = exitCode

    override fun destroy() {
        destroyed = true
    }

    override fun destroyForcibly(): Process {
        destroyed = true
        return this
    }

    override fun isAlive(): Boolean = isAlive

    override fun pid(): Long = 0L

    override fun supportsNormalTermination(): Boolean = true

    override fun onExit(): CompletableFuture<Process> = CompletableFuture.completedFuture(this)

    override fun toHandle(): ProcessHandle = throw UnsupportedOperationException()

    override fun info(): ProcessHandle.Info = throw UnsupportedOperationException()

    override fun children(): Stream<ProcessHandle> = throw UnsupportedOperationException()
}
