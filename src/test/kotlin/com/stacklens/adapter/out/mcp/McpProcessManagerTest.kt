package com.stacklens.adapter.out.mcp

import com.stacklens.application.port.out.mcp.McpEnvironmentPort
import com.stacklens.application.port.out.mcp.McpNodeLocator
import com.stacklens.application.port.out.mcp.McpOrphanProcessKillerPort
import com.stacklens.application.port.out.mcp.McpPortProbePort
import com.stacklens.application.port.out.mcp.McpProcessSpawnerPort
import com.stacklens.application.port.out.mcp.McpScriptLocator
import com.stacklens.application.port.out.process.SleeperPort
import com.stacklens.domain.model.McpServerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class McpProcessManagerTest {
    private val fakeEnv = FakeEnv()
    private val fakeProbe = FakeProbe()
    private val fakeKiller = FakeKiller()
    private val fakeScriptLocator = FakeScriptLocator()
    private val fakeNodeLocator = FakeNodeLocator()
    private val spawner = FakeSpawner()

    private fun newManager(): McpProcessManager =
        McpProcessManager(
            fakeEnv,
            spawner,
            fakeProbe,
            fakeKiller,
            fakeScriptLocator,
            fakeNodeLocator,
            SleeperPort { true },
        )

    private fun newConfig(
        baseUrl: String = "http://localhost:7456",
        authToken: String = "",
        jiraBaseUrl: String = "",
        confluenceBaseUrl: String = "",
        gitlabBaseUrl: String = "",
        githubToken: String = "",
        grafanaBaseUrl: String = "",
    ): McpServerConfig {
        val cfg = McpServerConfig()
        cfg.baseUrl = baseUrl
        cfg.authToken = authToken
        cfg.jiraBaseUrl = jiraBaseUrl
        cfg.confluenceBaseUrl = confluenceBaseUrl
        cfg.gitlabBaseUrl = gitlabBaseUrl
        cfg.githubApiBase = ""
        cfg.githubToken = githubToken
        cfg.grafanaBaseUrl = grafanaBaseUrl
        return cfg
    }

    @Test
    fun `status returns idle when no process has been started`() {
        val manager = newManager()
        val s = manager.status()
        assertFalse(s.running)
        assertNull(s.pid)
        assertNull(s.startedAt)
        assertNull(s.stoppedAt)
        assertNull(s.exitCode)
        assertEquals("idle", s.message)
        assertTrue(s.logs.isEmpty())
    }

    @Test
    fun `start reports error when script cannot be located`() {
        fakeScriptLocator.script = null
        val manager = newManager()
        val s = manager.start(newConfig())
        assertFalse(s.running)
        assertTrue(s.message.contains("mcp-server/dist/index.js not found"))
        assertTrue(s.logs.any { it.contains("[error]") && it.contains("not found") })
    }

    @Test
    fun `start reports error when node binary cannot be located`() {
        fakeScriptLocator.script = File("/tmp/fake.js")
        fakeNodeLocator.nodePath = null
        val manager = newManager()
        val s = manager.start(newConfig())
        assertFalse(s.running)
        assertTrue(s.message.contains("`node` >= 20 not found"))
    }

    @Test
    fun `start spawns process and reports running status`() {
        fakeScriptLocator.script = File("/tmp/fake.js")
        fakeNodeLocator.nodePath = "/usr/local/bin/node"
        spawner.scriptedProcess = FakeProcess(exitCode = 0, finished = false, alwaysAlive = true)
        spawner.spawned.clear()
        val manager = newManager()
        val s = manager.start(newConfig(baseUrl = "http://localhost:8123"))
        assertTrue(s.running)
        assertEquals("running", s.message)
        assertEquals(1, spawner.spawned.size)
        val spawned = spawner.spawned.first()
        assertEquals(listOf("/usr/local/bin/node", "/tmp/fake.js"), spawned.command)
        assertEquals("8123", spawned.environment["MCP_PORT"])
        assertEquals("127.0.0.1", spawned.environment["MCP_HOST"])
    }

    @Test
    fun `start forwards all non-blank config fields as environment variables`() {
        fakeScriptLocator.script = File("/tmp/fake.js")
        fakeNodeLocator.nodePath = "/usr/local/bin/node"
        spawner.spawned.clear()
        val manager = newManager()
        manager.start(
            newConfig(
                authToken = "auth",
                jiraBaseUrl = "jiraBase",
                confluenceBaseUrl = "confBase",
                gitlabBaseUrl = "gitlabBase",
                githubToken = "ghToken",
                grafanaBaseUrl = "grafBase",
            ),
        )
        val env = spawner.spawned.first().environment
        assertEquals("auth", env["MCP_AUTH_TOKEN"])
        assertEquals("jiraBase", env["JIRA_BASE_URL"])
        assertEquals("confBase", env["CONFLUENCE_BASE_URL"])
        assertEquals("gitlabBase", env["GITLAB_BASE_URL"])
        assertEquals("ghToken", env["GITHUB_TOKEN"])
        assertEquals("grafBase", env["GRAFANA_BASE_URL"])
    }

    @Test
    fun `start does NOT forward blank authToken, jiraUsername, etc as env vars`() {
        fakeScriptLocator.script = File("/tmp/fake.js")
        fakeNodeLocator.nodePath = "/usr/local/bin/node"
        spawner.spawned.clear()
        val manager = newManager()
        manager.start(newConfig())
        val env = spawner.spawned.first().environment
        assertFalse("MCP_AUTH_TOKEN" in env)
        assertFalse("JIRA_USERNAME" in env)
        assertFalse("JIRA_API_TOKEN" in env)
        assertFalse("CONFLUENCE_USERNAME" in env)
        assertFalse("CONFLUENCE_API_TOKEN" in env)
        assertFalse("GITLAB_ACCESS_TOKEN" in env)
        assertFalse("GRAFANA_API_KEY" in env)
        assertFalse("GITLAB_BASE_URL" in env)
        assertFalse("GITHUB_API_BASE" in env)
    }

    @Test
    fun `start is a no-op when a process is already running`() {
        fakeScriptLocator.script = File("/tmp/fake.js")
        fakeNodeLocator.nodePath = "/usr/local/bin/node"
        spawner.spawned.clear()
        val manager = newManager()
        manager.start(newConfig())
        assertEquals(1, spawner.spawned.size)
        val s2 = manager.start(newConfig())
        assertEquals(1, spawner.spawned.size)
        assertTrue(s2.logs.any { it.contains("start ignored") })
    }

    @Test
    fun `start kills orphan process when port is in use and then succeeds`() {
        fakeScriptLocator.script = File("/tmp/fake.js")
        fakeNodeLocator.nodePath = "/usr/local/bin/node"
        spawner.spawned.clear()
        fakeProbe.inUseBeforeKill.add(true)
        fakeProbe.inUseAfterKill.add(false)
        fakeKiller.killedPorts.clear()
        val manager = newManager()
        val s = manager.start(newConfig())
        assertTrue(s.running)
        assertEquals(1, fakeKiller.killedPorts.size)
        assertEquals(7456, fakeKiller.killedPorts.first())
    }

    @Test
    fun `start fails if port still in use after kill attempt`() {
        fakeScriptLocator.script = File("/tmp/fake.js")
        fakeNodeLocator.nodePath = "/usr/local/bin/node"
        spawner.spawned.clear()
        fakeProbe.inUseBeforeKill.add(true)
        fakeProbe.inUseAfterKill.add(true)
        val manager = newManager()
        val s = manager.start(newConfig())
        assertFalse(s.running)
        assertTrue(s.message.contains("port 7456 already in use"))
        assertTrue(spawner.spawned.isEmpty())
    }

    @Test
    fun `start catches exception from spawner and reports failed state`() {
        fakeScriptLocator.script = File("/tmp/fake.js")
        fakeNodeLocator.nodePath = "/usr/local/bin/node"
        spawner.throwOnSpawn = RuntimeException("boom")
        val manager = newManager()
        val s = manager.start(newConfig())
        assertFalse(s.running)
        assertTrue(s.message.startsWith("failed to start: boom"))
        assertTrue(s.logs.any { it.contains("failed to start") })
    }

    @Test
    fun `stop returns no-op status when nothing is running`() {
        val manager = newManager()
        val s = manager.stop()
        assertFalse(s.running)
        assertTrue(s.logs.any { it.contains("stop ignored") })
        assertEquals(0, fakeKiller.killedPorts.size)
    }

    @Test
    fun `stop kills orphan when managed process is gone but port still in use`() {
        fakeProbe.alwaysInUse = true
        fakeKiller.killedPorts.clear()
        val manager = newManager()
        val s = manager.stop()
        assertTrue(s.logs.any { it.contains("orphaned process") })
        assertEquals(1, fakeKiller.killedPorts.size)
        assertEquals(7456, fakeKiller.killedPorts.first())
        assertEquals("orphaned process on port 7456 killed", s.message)
    }

    @Test
    fun `stop destroys live process and waits up to 5 seconds`() {
        fakeScriptLocator.script = File("/tmp/fake.js")
        fakeNodeLocator.nodePath = "/usr/local/bin/node"
        val fakeProc = FakeProcess(exitCode = 0, finished = false, exitOnWait = true)
        spawner.scriptedProcess = fakeProc
        spawner.spawned.clear()
        val manager = newManager()
        manager.start(newConfig())
        val s = manager.stop()
        assertTrue(fakeProc.destroyed.get() >= 1)
        assertEquals(1, fakeProc.destroyed.get())
    }

    @Test
    fun `stop destroys forcibly when waitFor timeout exceeds 5 seconds`() {
        fakeScriptLocator.script = File("/tmp/fake.js")
        fakeNodeLocator.nodePath = "/usr/local/bin/node"
        val fakeProc = FakeProcess(exitCode = -1, finished = false, alwaysAlive = true)
        spawner.scriptedProcess = fakeProc
        spawner.spawned.clear()
        val manager = newManager()
        manager.start(newConfig())
        fakeProc.neverFinish = true
        val s = manager.stop()
        assertTrue(fakeProc.destroyed.get() >= 1)
        assertTrue(fakeProc.destroyForciblyCalled.get() >= 1)
    }

    @Test
    fun `stop catches InterruptedException and forces destruction`() {
        fakeScriptLocator.script = File("/tmp/fake.js")
        fakeNodeLocator.nodePath = "/usr/local/bin/node"
        val fakeProc = FakeProcess(exitCode = -1, finished = false, alwaysAlive = true, interruptOnWait = true)
        spawner.scriptedProcess = fakeProc
        spawner.spawned.clear()
        val manager = newManager()
        manager.start(newConfig())
        fakeProc.neverFinish = true
        val s = manager.stop()
        assertTrue(fakeProc.destroyForciblyCalled.get() >= 1)
        assertTrue(s.logs.any { it.contains("interrupted") })
    }

    @Test
    fun `PreDestroy shutdown is a no-op when nothing is running`() {
        val manager = newManager()
        manager.shutdown()
        val s = manager.status()
        assertFalse(s.running)
    }

    @Test
    fun `PreDestroy shutdown swallows exceptions from stop`() {
        fakeScriptLocator.script = File("/tmp/fake.js")
        fakeNodeLocator.nodePath = "/usr/local/bin/node"
        val fakeProc = FakeProcess(exitCode = -1, finished = false, alwaysAlive = true)
        spawner.scriptedProcess = fakeProc
        spawner.spawned.clear()
        val manager = newManager()
        manager.start(newConfig())
        manager.shutdown()
    }

    @Test
    fun `status includes pid and startedAt when running`() {
        fakeScriptLocator.script = File("/tmp/fake.js")
        fakeNodeLocator.nodePath = "/usr/local/bin/node"
        val fakeProc = FakeProcess(exitCode = 0, finished = false, alwaysAlive = true)
        spawner.scriptedProcess = fakeProc
        spawner.spawned.clear()
        val manager = newManager()
        val s = manager.start(newConfig())
        assertEquals(12345L, s.pid)
        assertNotNull(s.startedAt)
        assertNull(s.stoppedAt)
    }

    @Test
    fun `portFromBaseUrl parses standard URL`() {
        fakeScriptLocator.script = File("/tmp/fake.js")
        fakeNodeLocator.nodePath = "/usr/local/bin/node"
        spawner.spawned.clear()
        val manager = newManager()
        manager.start(newConfig(baseUrl = "http://example.com:8080/api"))
        assertEquals(
            8080,
            spawner.spawned
                .first()
                .environment["MCP_PORT"]
                ?.toInt(),
        )
    }

    @Test
    fun `portFromBaseUrl uses default 7456 for blank URL`() {
        fakeScriptLocator.script = File("/tmp/fake.js")
        fakeNodeLocator.nodePath = "/usr/local/bin/node"
        spawner.spawned.clear()
        val manager = newManager()
        manager.start(newConfig(baseUrl = ""))
        assertEquals(
            7456,
            spawner.spawned
                .first()
                .environment["MCP_PORT"]
                ?.toInt(),
        )
    }

    @Test
    fun `portFromBaseUrl uses default 7456 for malformed URL`() {
        fakeScriptLocator.script = File("/tmp/fake.js")
        fakeNodeLocator.nodePath = "/usr/local/bin/node"
        spawner.spawned.clear()
        val manager = newManager()
        manager.start(newConfig(baseUrl = "not a url"))
        assertEquals(
            7456,
            spawner.spawned
                .first()
                .environment["MCP_PORT"]
                ?.toInt(),
        )
    }

    @Test
    fun `portFromBaseUrl uses default 7456 for URL without explicit port`() {
        fakeScriptLocator.script = File("/tmp/fake.js")
        fakeNodeLocator.nodePath = "/usr/local/bin/node"
        spawner.spawned.clear()
        val manager = newManager()
        manager.start(newConfig(baseUrl = "http://localhost"))
        assertEquals(
            7456,
            spawner.spawned
                .first()
                .environment["MCP_PORT"]
                ?.toInt(),
        )
    }

    @Test
    fun `DefaultMcpScriptLocator returns null when no candidate exists`() {
        val env = FakeEnv().apply { simulateJavaHomeExists = false }
        val locator = DefaultMcpScriptLocator(env)
        val result = locator.locateScript()
        assertTrue(result == null || result.exists())
    }

    @Test
    fun `DefaultMcpScriptLocator respects STACKLENS_MCP_SCRIPT env override`() {
        val custom = File.createTempFile("override", ".js")
        custom.writeText("//")
        val env = FakeEnv().apply { overridePath = custom.absolutePath }
        val locator = DefaultMcpScriptLocator(env)
        val result = locator.locateScript()
        assertEquals(custom.absolutePath, result?.absolutePath)
        custom.delete()
    }

    @Test
    fun `DefaultMcpNodeLocator returns null when no node is available`() {
        val env =
            FakeEnv().apply {
                pathEntries = emptyList()
                simulateJavaHomeExists = false
                customUserHome = "/tmp/no-such-home-${System.currentTimeMillis()}"
            }
        val locator = DefaultMcpNodeLocator(env, FakeSpawner())
        val result = locator.locateNode()
        assertTrue(result == null || File(result).isFile)
    }

    private class FakeEnv : McpEnvironmentPort {
        var executableScript: File? = null
        var nodeBinary: String? = null
        var overridePath: String? = null
        var configuredPort = AtomicInteger(7456)
        var pathEntries: List<String> = emptyList()
        var simulateMacOsLayout = false
        var simulateLinuxJpackageLayout = false
        var simulateShellLookupResult: String? = null
        var simulateOptHomebrewNode = false
        var simulateUsrLocalBinNode = false
        var simulateNvmAtHome: File? = null
        var simulateBundledGradleNode = false
        var simulateJavaHomeExists = true
        var customUserHome: String? = null

        override fun getCurrentDir(): File = File(".").absoluteFile.parentFile ?: File(".")

        override fun getJavaHome(): File? =
            when {
                simulateMacOsLayout -> {
                    val fakeHome = File("/tmp/fakeapp/Contents/runtime/Contents/Home")
                    fakeHome.mkdirs()
                    fakeHome
                }
                simulateLinuxJpackageLayout -> {
                    val fakeHome = File("/tmp/fakeapp/lib/runtime")
                    fakeHome.mkdirs()
                    fakeHome
                }
                simulateJavaHomeExists -> System.getProperty("java.home")?.let(::File)
                else -> null
            }

        override fun getUserHome(): String = customUserHome ?: System.getProperty("user.home") ?: "/tmp"

        override fun getOsName(): String = "Mac OS X"

        override fun getEnv(name: String): String? =
            when (name) {
                "STACKLENS_MCP_SCRIPT" -> overridePath
                "PATH" -> pathEntries.joinToString(File.pathSeparator)
                else -> null
            }

        override fun getProperty(name: String): String? =
            when (name) {
                "stacklens.mcp.script" -> overridePath
                else -> null
            }

        override fun getProcessPid(): Long = 99999L

        override fun getProcessExecutable(): String? = "/usr/bin/java"
    }

    private class FakeProbe : McpPortProbePort {
        val inUseBeforeKill = ArrayDeque<Boolean>()
        val inUseAfterKill = ArrayDeque<Boolean>()
        var alwaysInUse = false

        override fun isInUse(
            host: String,
            port: Int,
            timeoutMs: Int,
        ): Boolean {
            if (alwaysInUse) return true
            return when {
                inUseBeforeKill.isNotEmpty() -> inUseBeforeKill.removeFirst()
                inUseAfterKill.isNotEmpty() -> inUseAfterKill.removeFirst()
                else -> false
            }
        }
    }

    private class FakeKiller : McpOrphanProcessKillerPort {
        val killedPorts = mutableListOf<Int>()

        override fun killProcessOnPort(port: Int) {
            killedPorts += port
        }
    }

    private class FakeScriptLocator : McpScriptLocator {
        var script: File? = null

        override fun locateScript(): File? = script
    }

    private class FakeNodeLocator : McpNodeLocator {
        var nodePath: String? = null

        override fun locateNode(): String? = nodePath
    }

    private data class SpawnRecord(
        val command: List<String>,
        val workingDir: File?,
        val environment: Map<String, String>,
    )

    private class FakeSpawner : McpProcessSpawnerPort {
        val spawned = mutableListOf<SpawnRecord>()
        var throwOnSpawn: Exception? = null
        var scriptedProcess: Process? = null
        var scriptedExitCode: Int = 0
        val scriptedOutputFor = mutableMapOf<String, String>()

        override fun spawn(
            command: List<String>,
            workingDir: File?,
            environment: Map<String, String>,
            redirectErrorStream: Boolean,
        ): Process {
            throwOnSpawn?.let { throw it }
            val env = environment.toMap()
            spawned += SpawnRecord(command, workingDir, env)
            return scriptedProcess ?: FakeProcess(scriptedExitCode, finished = false)
        }

        override fun runAndCapture(
            command: List<String>,
            workingDir: File?,
            timeoutSeconds: Long,
        ): String {
            val exact = scriptedOutputFor.entries.firstOrNull { it.key == command.firstOrNull() }
            if (exact != null) return exact.value
            return try {
                val pb = ProcessBuilder(command)
                if (workingDir != null) pb.directory(workingDir)
                pb.redirectErrorStream(true)
                val proc = pb.start()
                val out =
                    proc.inputStream
                        .bufferedReader()
                        .readText()
                        .trim()
                proc.waitFor(timeoutSeconds, TimeUnit.SECONDS)
                out
            } catch (e: Exception) {
                ""
            }
        }
    }

    private class FakeProcess(
        val exitCode: Int,
        @Suppress("unused") val finished: Boolean,
        val alwaysAlive: Boolean = false,
        val interruptOnWait: Boolean = false,
        val exitOnWait: Boolean = false,
    ) : Process() {
        var neverFinish = false
        val destroyed = AtomicInteger(0)
        val destroyForciblyCalled = AtomicInteger(0)
        private val aliveFlag = AtomicInteger(1)
        private val inputBytes = ByteArrayInputStream(ByteArray(0))

        init {
            if (alwaysAlive) aliveFlag.set(1)
        }

        override fun getOutputStream(): OutputStream =
            object : OutputStream() {
                override fun write(b: Int) {}
            }

        override fun getInputStream(): InputStream = inputBytes

        override fun getErrorStream(): InputStream = inputBytes

        override fun waitFor(): Int {
            if (interruptOnWait) throw InterruptedException("test")
            if (neverFinish) {
                try {
                    Thread.sleep(20)
                } catch (_: InterruptedException) {
                    throw InterruptedException("test")
                }
                throw InterruptedException("test")
            }
            if (alwaysAlive) {
                while (aliveFlag.get() == 1) {
                    try {
                        Thread.sleep(5)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw InterruptedException("test")
                    }
                }
            }
            return exitCode
        }

        override fun waitFor(
            timeout: Long,
            unit: TimeUnit,
        ): Boolean {
            if (interruptOnWait) throw InterruptedException("test")
            if (neverFinish) return false
            if (alwaysAlive) return true
            aliveFlag.set(0)
            return true
        }

        override fun exitValue(): Int = exitCode

        override fun destroy() {
            destroyed.incrementAndGet()
            aliveFlag.set(0)
        }

        override fun destroyForcibly(): Process {
            destroyForciblyCalled.incrementAndGet()
            aliveFlag.set(0)
            return this
        }

        override fun isAlive(): Boolean = aliveFlag.get() == 1

        override fun pid(): Long = 12345L

        override fun info(): ProcessHandle.Info = ProcessHandle.current().info()

        override fun supportsNormalTermination(): Boolean = false

        override fun children(): java.util.stream.Stream<ProcessHandle> =
            java.util.stream.Stream
                .empty()

        override fun descendants(): java.util.stream.Stream<ProcessHandle> =
            java.util.stream.Stream
                .empty()

        override fun onExit(): java.util.concurrent.CompletableFuture<Process> =
            java.util.concurrent.CompletableFuture
                .completedFuture(this)

        override fun toHandle(): ProcessHandle = ProcessHandle.current()
    }
}
