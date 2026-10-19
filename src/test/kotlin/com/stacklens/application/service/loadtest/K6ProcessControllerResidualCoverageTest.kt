package com.stacklens.application.service.loadtest
import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.adapter.out.loadtest.K6ScriptTransformerAdapter
import com.stacklens.application.port.out.LoadTestAnalysisPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.loadtest.LocalBaseUrlPort
import com.stacklens.application.port.out.process.ProcessRunPort
import com.stacklens.application.port.out.textfilter.OutputFilterPort
import com.stacklens.application.service.loadtest.LoadTestAi
import com.stacklens.domain.model.LoadTestAnalysis
import com.stacklens.domain.text.filter.FilterResult
import com.stacklens.testsupport.FakeExecutableLookupPort
import com.stacklens.testsupport.FakeI18n
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class K6ProcessControllerResidualCoverageTest {
    private val analysisRepo = mock<LoadTestAnalysisPort>()

    private val aiService = mock<LoadTestAi>()

    private val i18n: I18nPort = FakeI18n()

    private val processRunPort = mock<ProcessRunPort>()

    private val baseUrlPort =
        mock<LocalBaseUrlPort>().also {
            Mockito
                .lenient()
                .doReturn("http://localhost:8090")
                .`when`(it)
                .currentBaseUrl()
        }

    private fun controller(
        outputFilter: OutputFilterPort = mock(),
        executables: FakeExecutableLookupPort = FakeExecutableLookupPort(resolved = mapOf("k6" to "/usr/bin/k6")),
    ) = testK6ProcessController(
        analysisRepo,
        aiService,
        i18n,
        processRunPort,
        localBaseUrl = baseUrlPort,
        scriptTransformer = K6ScriptTransformerAdapter(baseUrlPort),
        outputFilter = outputFilter,
        executables = executables,
        fileSystem = JavaFileSystemAdapter(),
    )

    private class FakeProc(
        private val alive: Boolean = false,
        private val code: Int = 0,
        private val finishedWithinTimeout: Boolean = true,
    ) : Process() {
        override fun getOutputStream(): OutputStream = OutputStream.nullOutputStream()

        override fun getInputStream(): InputStream = ByteArrayInputStream(ByteArray(0))

        override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))

        override fun waitFor(): Int = code

        override fun waitFor(
            timeout: Long,
            unit: TimeUnit,
        ): Boolean = finishedWithinTimeout

        override fun exitValue(): Int = code

        override fun destroy() {}

        override fun isAlive(): Boolean = alive

        override fun destroyForcibly(): Process = this
    }

    private class PhantomFile(
        path: String,
    ) : File(path) {
        override fun exists(): Boolean = true

        override fun length(): Long = 5L
    }

    private class UndeletableFile(
        path: String,
    ) : File(path) {
        override fun delete(): Boolean = throw IOException("delete refused")
    }

    private fun k6RunClass(): Class<*> = Class.forName("com.stacklens.application.service.loadtest.K6Run")

    private fun newRun(
        process: Process,
        stdout: String,
        stderr: String,
        summaryFile: File,
        scriptHeader: String?,
    ): Any = newRun(process, StringBuilder(stdout), StringBuilder(stderr), summaryFile, scriptHeader)

    private fun newRun(
        process: Process,
        stdout: StringBuilder,
        stderr: StringBuilder,
        summaryFile: File,
        scriptHeader: String?,
    ): Any {
        val constructor = k6RunClass().declaredConstructors.first { it.parameterCount == 5 }
        constructor.isAccessible = true
        return constructor.newInstance(
            com.stacklens.adapter.out.shared
                .JvmManagedProcess(process),
            stdout,
            stderr,
            summaryFile,
            scriptHeader,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun registerRun(
        target: K6ProcessController,
        analysisId: Long,
        run: Any,
    ) {
        val registryField = K6ProcessController::class.java.getDeclaredField("runRegistry")
        registryField.isAccessible = true
        val registry = registryField.get(target)
        val runsField = registry.javaClass.getDeclaredField("runs")
        runsField.isAccessible = true
        (runsField.get(registry) as MutableMap<Long, Any>)[analysisId] = run
    }

    @Test
    fun `start failure without an exception message still reports the localized error`() {
        whenever(processRunPort.launch(any())).thenThrow(RuntimeException())

        val result = controller().startK6Script(1L, null, null, "SCRIPT")

        assertFalse(result.running)
        assertEquals("i18n:load_test.be_k6_start_error|", result.error)
    }

    @Test
    fun `environment patching stops when the analysis vanished in the meantime`() {
        whenever(processRunPort.launch(any())).thenReturn(
            com.stacklens.adapter.out.shared
                .JvmManagedProcess(FakeProc(alive = true)),
        )
        whenever(analysisRepo.findLoadTestAnalysisById(7L)).thenReturn(null)

        val result = controller().startK6Script(7L, null, "stage", "const BASE_URL = 'http://template'")

        assertTrue(result.running)
        verify(analysisRepo).findLoadTestAnalysisById(7L)
    }

    @Test
    fun `the controller exposes the base url literal of the transformer`() {
        val target = controller()

        assertEquals("http://x", target.extractBaseUrlLiteral("var BASE_URL = 'http://x'"))
        assertNull(target.extractBaseUrlLiteral("const OTHER = 1"))
    }

    @Test
    fun `a process that ignores the hard timeout is killed as a tree`() {
        whenever(processRunPort.launch(any())).thenReturn(
            com.stacklens.adapter.out.shared
                .JvmManagedProcess(FakeProc(alive = true, finishedWithinTimeout = false)),
        )

        controller().startK6Script(11L, null, null, "SCRIPT")

        verify(processRunPort, Mockito.timeout(10_000)).killProcessTree(any())
    }

    @Test
    fun `a failing process-tree kill during the hard timeout is swallowed`() {
        whenever(processRunPort.launch(any())).thenReturn(
            com.stacklens.adapter.out.shared
                .JvmManagedProcess(FakeProc(alive = true, finishedWithinTimeout = false)),
        )
        doThrow(RuntimeException("kill refused")).whenever(processRunPort).killProcessTree(any())

        controller().startK6Script(12L, null, null, "SCRIPT")

        verify(processRunPort, Mockito.timeout(10_000)).killProcessTree(any())
    }

    @Test
    fun `a process that finishes within the hard timeout keeps running untouched`() {
        whenever(processRunPort.launch(any())).thenReturn(
            com.stacklens.adapter.out.shared
                .JvmManagedProcess(FakeProc(alive = true, finishedWithinTimeout = true)),
        )

        val result = controller().startK6Script(13L, null, null, "SCRIPT")

        assertTrue(result.running)
        verify(processRunPort, Mockito.after(2_000).never()).killProcessTree(any())
    }

    @Test
    fun `status returns the summary file content and removes the finished run`() {
        val summary = Files.createTempFile("residual-summary", ".json").toFile()
        summary.writeText("""{"root_group":{}}""")
        val target = controller()
        registerRun(target, 20L, newRun(FakeProc(), "OUT", "ERR", summary, "HEADER"))
        val analysis = LoadTestAnalysis(id = 20L, status = "COMPLETED")
        whenever(analysisRepo.findLoadTestAnalysisById(20L)).thenReturn(analysis)

        val result = target.getK6Status(20L)

        assertFalse(result.running)
        assertEquals("""{"root_group":{}}""", result.summaryJson)
        assertEquals("HEADER\nOUT", analysis.k6Output)
        assertEquals("ERR", analysis.k6Error)
        assertEquals(0, analysis.k6ExitCode!!)
        assertFalse(summary.exists())
    }

    @Test
    fun `status without a script header keeps the filtered output as it is`() {
        val target = controller()
        registerRun(target, 21L, newRun(FakeProc(), "OUT", "", File("missing-summary.json"), null))
        val analysis = LoadTestAnalysis(id = 21L, status = "COMPLETED")
        whenever(analysisRepo.findLoadTestAnalysisById(21L)).thenReturn(analysis)

        val result = target.getK6Status(21L)

        assertNull(result.summaryJson)
        assertEquals("OUT", analysis.k6Output)
        assertEquals("", analysis.k6Error)
    }

    @Test
    fun `status ignores a summary file that is too short to hold a report`() {
        val tiny = Files.createTempFile("residual-tiny", ".json").toFile()
        tiny.writeText("{}")
        val target = controller()
        registerRun(target, 22L, newRun(FakeProc(), "", "", tiny, "HEADER"))

        val result = target.getK6Status(22L)

        assertNull(result.summaryJson)
        assertFalse(tiny.exists())
    }

    @Test
    fun `status survives a summary file that cannot be read`() {
        val target = controller()
        registerRun(target, 23L, newRun(FakeProc(), "", "", PhantomFile("/tmp/does-not-exist-summary.json"), "HEADER"))

        val result = target.getK6Status(23L)

        assertNull(result.summaryJson)
    }

    @Test
    fun `status survives a summary file that cannot be deleted`() {
        val lingering = Files.createTempFile("residual-undeletable", ".json").toFile()
        lingering.writeText("""{"a":1}""")
        val target = controller()
        registerRun(target, 24L, newRun(FakeProc(), "", "", UndeletableFile(lingering.absolutePath), "HEADER"))

        val result = target.getK6Status(24L)

        assertEquals("""{"a":1}""", result.summaryJson)
        assertTrue(lingering.exists())
        lingering.delete()
    }

    @Test
    fun `status skips persistence when the analysis is gone`() {
        val target = controller()
        registerRun(target, 25L, newRun(FakeProc(), "OUT", "ERR", File("gone.json"), "HEADER"))
        whenever(analysisRepo.findLoadTestAnalysisById(25L)).thenReturn(null)

        val result = target.getK6Status(25L)

        assertEquals("OUT", result.output)
        verify(analysisRepo, Mockito.never()).saveLoadTestAnalysis(any())
    }

    @Test
    fun `status logs and continues when persistence blows up`() {
        val target = controller()
        registerRun(target, 26L, newRun(FakeProc(), "OUT", "ERR", File("gone.json"), "HEADER"))
        whenever(analysisRepo.findLoadTestAnalysisById(26L)).thenReturn(LoadTestAnalysis(id = 26L, status = "X"))
        doThrow(RuntimeException("db down")).whenever(analysisRepo).saveLoadTestAnalysis(any())

        val result = target.getK6Status(26L)

        assertEquals("OUT", result.output)
    }

    @Test
    fun `status filters only the streams that actually carry output`() {
        val filter = mock<OutputFilterPort>()
        whenever(filter.enabled).thenReturn(true)
        whenever(filter.filter(any(), any(), any())).thenAnswer { invocation ->
            FilterResult("F(" + invocation.getArgument<String>(1) + ")", 10, 5, "k6-spec")
        }
        val silent = LoadTestAnalysis(id = 27L, status = "X")
        whenever(analysisRepo.findLoadTestAnalysisById(27L)).thenReturn(silent)
        val target = controller(outputFilter = filter)
        registerRun(target, 27L, newRun(FakeProc(), "", "boom", File("gone.json"), null))

        val partial = target.getK6Status(27L)

        assertEquals("boom", partial.error)
        assertEquals("", silent.k6Output)
        assertEquals("F(boom)", silent.k6Error)

        val loud = LoadTestAnalysis(id = 28L, status = "X")
        whenever(analysisRepo.findLoadTestAnalysisById(28L)).thenReturn(loud)
        val other = controller(outputFilter = filter)
        registerRun(other, 28L, newRun(FakeProc(), "noise", "", File("gone.json"), null))

        val flipped = other.getK6Status(28L)

        assertEquals("noise", flipped.output)
        assertEquals("F(noise)", loud.k6Output)
        assertEquals("", loud.k6Error)
    }

    @Test
    fun `the internal run record behaves like a data carrier`() {
        val process = FakeProc()
        val stdout = StringBuilder("a")
        val stderr = StringBuilder("b")
        val summary = File("run-summary.json")
        val first = newRun(process, stdout, stderr, summary, "header")
        val second = newRun(process, stdout, stderr, summary, "header")
        val klass = k6RunClass()

        assertNotNull(first)
        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertTrue(first.toString().contains("K6Run"))
        assertFalse(first.equals(second.toString()))
        assertFalse(first.equals(newRun(process, stdout, stderr, summary, null)))

        klass
            .declaredMethods
            .filter { it.name.startsWith("component") && it.parameterCount == 0 }
            .forEach { method ->
                method.isAccessible = true
                assertNotNull(method.invoke(first))
            }

        val copy =
            klass.getDeclaredMethod(
                "copy",
                com.stacklens.application.port.out.process.ManagedProcess::class.java,
                StringBuilder::class.java,
                StringBuilder::class.java,
                File::class.java,
                String::class.java,
            )
        copy.isAccessible = true
        assertEquals(
            first,
            copy.invoke(
                first,
                com.stacklens.adapter.out.shared
                    .JvmManagedProcess(process),
                stdout,
                stderr,
                summary,
                "header",
            ),
        )

        val copyDefault =
            klass
                .declaredMethods
                .first { it.name == "copy\$default" }
        copyDefault.isAccessible = true
        assertNotNull(copyDefault.invoke(null, first, null, null, null, null, null, 31, null))

        val defaults = klass.declaredConstructors.first { it.parameterCount == 7 }
        defaults.isAccessible = true
        val implicit =
            defaults.newInstance(
                com.stacklens.adapter.out.shared
                    .JvmManagedProcess(process),
                stdout,
                stderr,
                summary,
                "ignored",
                16,
                null,
            )
        assertEquals(newRun(process, stdout, stderr, summary, null), implicit)
    }
}
