package com.stacklens.desktop

import com.stacklens.application.port.out.platform.OsType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.lang.management.ManagementFactory
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

class ExitSeamChildProcessCoverageTest {
    private val probeTerminationSeconds = 30L

    private fun probe(vararg case: String): Pair<Int, String> {
        val launch = launchProbe(case.toList())
        val output = ProbeOutput(launch.process)
        val finished = launch.process.waitFor(probeTerminationSeconds, TimeUnit.SECONDS)
        val terminationNote =
            if (finished) {
                ""
            } else {
                requestThreadDumpFrom(launch.process)
            }
        output.awaitDrain(5_000)
        val collected = output.text()
        launch.writeDiagnostics(case.joinToString(" "), collected)
        if (!finished) {
            launch.process.destroyForcibly().waitFor(5, TimeUnit.SECONDS)
        }
        val caseLabel = case.joinToString(" ")
        assertTrue(
            finished,
            "the probe JVM kept running past ${probeTerminationSeconds}s: case=[$caseLabel] $terminationNote output=$collected",
        )
        return launch.process.exitValue() to collected
    }

    private fun requestThreadDumpFrom(process: Process): String {
        val pid = process.pid()
        val os = currentOs()
        if (os == OsType.WINDOWS) {
            return "no thread dump available on $os for pid $pid"
        }
        return runCatching {
            ProcessBuilder("kill", "-3", pid.toString())
                .start()
                .waitFor(5, TimeUnit.SECONDS)
        }.fold(
            { "SIGQUIT sent to pid $pid, its JVM thread dump is included in the output below;" },
            { "SIGQUIT to pid $pid failed: $it;" },
        )
    }

    private fun currentOs(): OsType = OsType.fromOsName(System.getProperty("os.name", "").lowercase())

    private fun launchProbe(case: List<String>): ProbeLaunch {
        val agentJar = jacocoAgentJar()
        val childArgs = mutableListOf(javaBinary(), "-Djava.awt.headless=false", "--enable-native-access=ALL-UNNAMED")
        if (currentOs() == OsType.MACOS) {
            childArgs += "-Dglass.taskbarApplication=false"
        }
        val childClasspath = StringBuilder(System.getProperty("java.class.path"))
        if (agentJar != null) {
            childArgs += "-javaagent:$agentJar=destfile=${childExecutionData(case.first()).absolutePath}"
            childClasspath.append(File.pathSeparator).append(agentJar)
        }
        val process =
            ProcessBuilder(
                childArgs +
                    listOf("-cp", childClasspath.toString(), ExitSeamProbe::class.java.name) +
                    case,
            ).redirectErrorStream(true)
                .start()
        return ProbeLaunch(process, agentJar)
    }

    private fun javaBinary(): String = Paths.get(System.getProperty("java.home"), "bin", "java").toString()

    private inner class ProbeLaunch(
        val process: Process,
        private val agentJar: String?,
    ) {
        fun writeDiagnostics(
            caseLabel: String,
            output: String,
        ) {
            File("build/jacoco/children/diag-$caseLabel.txt").writeText(output + "\nagent=" + agentJar)
        }
    }

    private fun jacocoAgentJar(): String? =
        ManagementFactory
            .getRuntimeMXBean()
            .inputArguments
            .firstOrNull { it.startsWith("-javaagent:") && it.contains("jacoco") }
            ?.removePrefix("-javaagent:")
            ?.substringBefore("=")

    private fun childExecutionData(case: String): File {
        val directory = File("build/jacoco/children").apply { mkdirs() }
        return File(directory, "child-$case-${ProcessHandle.current().pid()}.exec")
    }

    private class ProbeOutput(
        private val process: Process,
    ) {
        private val collected = StringBuilder()
        private val drain =
            Thread {
                runCatching {
                    process.inputStream.reader(Charsets.UTF_8).forEachLine { line ->
                        synchronized(collected) { collected.appendLine(line) }
                    }
                }
            }.apply {
                isDaemon = true
                name = "probe-output-drain"
                start()
            }

        fun awaitDrain(timeoutMillis: Long) {
            drain.join(timeoutMillis)
        }

        fun text(): String = synchronized(collected) { collected.toString() }
    }

    @Test
    fun `the lifecycle controller halts the jvm with the requested code`() {
        assertEquals(4, probe("app-lifecycle-halt").first)
    }

    @Test
    fun `the startup error reporter exits with the requested code`() {
        assertEquals(5, probe("reporter-exit").first)
    }

    @Test
    fun `the shutdown handler sleeps through the default seam`() {
        val (code, output) = probe("shutdown-sleep")

        assertEquals(0, code)
        assertTrue(output.contains("slept"), output)
    }

    @Test
    fun `the shutdown handler halts the jvm once the backend is gone`() {
        assertEquals(0, probe("shutdown-halt").first)
    }

    @Test
    fun `the quit coordinator sleeps through the default seam`() {
        val (code, output) = probe("quit-sleep")

        assertEquals(0, code)
        assertTrue(output.contains("slept"), output)
    }

    @Test
    fun `the quit coordinator halts the jvm after the shutdown grace period`() {
        assertEquals(0, probe("quit-halt").first)
    }

    @Test
    fun `the browser launcher resolves the desktop of this platform`() {
        val (code, output) = probe("browser-desktop")

        assertEquals(0, code)
        assertTrue(output.contains("desktop=true"), output)
    }

    @Test
    fun `the mac dock icon helper walks its default taskbar seam`() {
        val (code, output) = probe("mac-dock-icon")

        assertEquals(0, code)
        assertTrue(output.contains("dock-icon-set"), output)
    }

    @Test
    fun `the quit coordinator leaves the javafx platform through the default seam`() {
        val (code, output) = probe("quit-exitfx")

        assertEquals(0, code)
        assertTrue(output.contains("fx-exited"), output)
    }
}
