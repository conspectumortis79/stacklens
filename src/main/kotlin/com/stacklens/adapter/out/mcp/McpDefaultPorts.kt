package com.stacklens.adapter.out.mcp

import com.stacklens.adapter.out.shared.ThreadSleepSleeper
import com.stacklens.application.port.out.mcp.McpEnvironmentPort
import com.stacklens.application.port.out.mcp.McpOrphanProcessKillerPort
import com.stacklens.application.port.out.mcp.McpPortProbePort
import com.stacklens.application.port.out.mcp.McpProcessSpawnerPort
import com.stacklens.application.port.out.platform.OsType
import com.stacklens.application.port.out.process.SleeperPort
import com.stacklens.shared.ProcessUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

@Component
class DefaultMcpEnvironmentPort(
    private val systemPropertyReader: (String) -> String? = System::getProperty,
    private val environmentVariableReader: (String) -> String? = System::getenv,
    private val currentDirProvider: () -> File = { File(System.getProperty("user.dir")) },
    private val javaHomeProvider: () -> String? = { System.getProperty("java.home") },
    private val userHomeProvider: () -> String? = { System.getProperty("user.home") },
    private val osNameProvider: () -> String? = { System.getProperty("os.name") },
    private val processPidProvider: () -> Long = { ProcessHandle.current().pid() },
    private val processExecutableProvider: () -> String? = {
        ProcessHandle
            .current()
            .info()
            .command()
            .orElse(null)
    },
) : McpEnvironmentPort {
    override fun getCurrentDir(): File = currentDirProvider()

    override fun getJavaHome(): File? = javaHomeProvider()?.let(::File)

    override fun getUserHome(): String = userHomeProvider() ?: ""

    override fun getOsName(): String = osNameProvider() ?: ""

    override fun getEnv(name: String): String? = environmentVariableReader(name)

    override fun getProperty(name: String): String? = systemPropertyReader(name)

    override fun getProcessPid(): Long = processPidProvider()

    override fun getProcessExecutable(): String? = processExecutableProvider()
}

@Component
class DefaultMcpProcessSpawnerPort : McpProcessSpawnerPort {
    private val log = LoggerFactory.getLogger(DefaultMcpProcessSpawnerPort::class.java)

    override fun spawn(
        command: List<String>,
        workingDir: File?,
        environment: Map<String, String>,
        redirectErrorStream: Boolean,
    ): Process {
        require(command.isNotEmpty()) { "command must contain at least one element" }
        val pb = ProcessBuilder(command)
        if (workingDir != null) pb.directory(workingDir)
        pb.redirectErrorStream(redirectErrorStream)
        environment.forEach { (k, v) -> pb.environment()[k] = v }
        return pb.start()
    }

    override fun runAndCapture(
        command: List<String>,
        workingDir: File?,
        timeoutSeconds: Long,
    ): String {
        require(command.isNotEmpty()) { "command must contain at least one element" }
        val captureFile = File.createTempFile("stacklens-mcp-capture-", ".log")
        val pb = ProcessBuilder(command)
        if (workingDir != null) pb.directory(workingDir)
        pb.redirectErrorStream(true)
        pb.redirectOutput(ProcessBuilder.Redirect.to(captureFile))
        return try {
            val proc = pb.start()
            try {
                if (!proc.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                    log.warn("runAndCapture timed out after {}s: {}", timeoutSeconds, command)
                    return ""
                }
                if (proc.exitValue() != 0) {
                    log.debug("runAndCapture exited with {} for {}", proc.exitValue(), command)
                    return ""
                }
                captureFile.readText(Charsets.UTF_8).trim()
            } finally {
                if (proc.isAlive) ProcessUtils.killProcessTree(proc)
                proc.destroy()
            }
        } catch (e: Exception) {
            log.debug("runAndCapture failed for {}: {}", command, e.message)
            ""
        } finally {
            captureFile.delete()
        }
    }
}

@Component
class DefaultMcpPortProbePort : McpPortProbePort {
    override fun isInUse(
        host: String,
        port: Int,
        timeoutMs: Int,
    ): Boolean =
        runCatching {
            Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
            true
        }.getOrDefault(false)
}

@Component
class DefaultMcpOrphanProcessKillerPort(
    private val env: McpEnvironmentPort = DefaultMcpEnvironmentPort(),
    private val portProbe: McpPortProbePort = DefaultMcpPortProbePort(),
    private val spawner: McpProcessSpawnerPort = DefaultMcpProcessSpawnerPort(),
    private val sleeper: SleeperPort = ThreadSleepSleeper(),
) : McpOrphanProcessKillerPort {
    private val log = LoggerFactory.getLogger(DefaultMcpOrphanProcessKillerPort::class.java)

    private companion object {
        private const val ORPHAN_EXIT_WAIT_MS = 3000L
        private const val POLL_INTERVAL_MS = 200L
        private const val PROBE_TIMEOUT_MS = 200
    }

    override fun killProcessOnPort(port: Int) {
        val isWin = OsType.fromOsName(env.getOsName()) == OsType.WINDOWS
        val ownPid = env.getProcessPid().toString()
        runCatching {
            if (isWin) {
                val out =
                    spawner.runAndCapture(
                        listOf("cmd", "/c", "netstat -ano"),
                        null,
                        3,
                    )
                val pidPattern = Regex("""TCP\s+\S+:$port\s+\S+\s+LISTENING\s+(\d+)""")
                pidPattern.findAll(out).forEach { m ->
                    val pid = m.groupValues[1]
                    if (pid == ownPid) {
                        log.warn("refusing to kill own PID {} on port {}", pid, port)
                        return@forEach
                    }
                    spawner.spawn(listOf("taskkill", "/F", "/PID", pid), null, emptyMap())
                }
            } else {
                val out =
                    spawner.runAndCapture(
                        listOf("lsof", "-ti", ":$port"),
                        null,
                        3,
                    )
                val pids =
                    out
                        .lines()
                        .filter { it.isNotBlank() }
                pids.forEach { pid ->
                    if (pid == ownPid) {
                        log.warn("refusing to kill own PID {} on port {}", pid, port)
                        return@forEach
                    }
                    val ppid =
                        spawner
                            .runAndCapture(
                                listOf("ps", "-o", "ppid=", "-p", pid),
                                null,
                                3,
                            ).trim()
                    if (ppid.isNotEmpty() && ppid != "1" && ppid != ownPid) {
                        log.warn(
                            "port {} is held by PID {} whose parent is {} (not us={}, not launchd=1) — refusing to kill to avoid collateral damage",
                            port,
                            pid,
                            ppid,
                            ownPid,
                        )
                        return@forEach
                    }
                    log.info("killing orphaned PID {} on port {} (ppid={})", pid, port, ppid)
                    spawner.spawn(listOf("kill", pid), null, emptyMap())
                    var waited = 0L
                    while (waited < ORPHAN_EXIT_WAIT_MS && portProbe.isInUse("127.0.0.1", port, PROBE_TIMEOUT_MS)) {
                        if (!sleeper.sleep(POLL_INTERVAL_MS)) {
                            Thread.currentThread().interrupt()
                            break
                        }
                        waited += POLL_INTERVAL_MS
                    }
                    if (portProbe.isInUse("127.0.0.1", port, PROBE_TIMEOUT_MS)) {
                        spawner.spawn(listOf("kill", "-9", pid), null, emptyMap())
                    }
                }
            }
        }.onFailure { e -> log.warn("failed to kill process on port $port: ${e.message}") }
    }
}
