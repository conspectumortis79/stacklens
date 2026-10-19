package com.stacklens.adapter.out.mcp

import com.stacklens.adapter.out.shared.ThreadSleepSleeper
import com.stacklens.application.port.out.McpProcessControlPort
import com.stacklens.application.port.out.McpProcessControlPort.Status
import com.stacklens.application.port.out.mcp.McpEnvironmentPort
import com.stacklens.application.port.out.mcp.McpNodeLocator
import com.stacklens.application.port.out.mcp.McpOrphanProcessKillerPort
import com.stacklens.application.port.out.mcp.McpPortProbePort
import com.stacklens.application.port.out.mcp.McpProcessSpawnerPort
import com.stacklens.application.port.out.mcp.McpScriptLocator
import com.stacklens.application.port.out.process.SleeperPort
import com.stacklens.domain.model.McpServerConfig
import com.stacklens.domain.util.closingAuto
import com.stacklens.domain.util.quietly
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URI
import java.time.Instant
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@Component
open class McpProcessManager(
    private val env: McpEnvironmentPort = DefaultMcpEnvironmentPort(),
    private val spawner: McpProcessSpawnerPort = DefaultMcpProcessSpawnerPort(),
    private val portProbe: McpPortProbePort = DefaultMcpPortProbePort(),
    private val orphanKiller: McpOrphanProcessKillerPort = DefaultMcpOrphanProcessKillerPort(),
    private val scriptLocator: McpScriptLocator = DefaultMcpScriptLocator(env),
    private val nodeLocator: McpNodeLocator = DefaultMcpNodeLocator(env, spawner),
    private val sleeper: SleeperPort = ThreadSleepSleeper(),
) : McpProcessControlPort {
    private val log = LoggerFactory.getLogger(McpProcessManager::class.java)
    private val process = AtomicReference<Process?>(null)
    private val pidRef = AtomicReference<Long?>(null)
    private val startedAt = AtomicReference<Instant?>(null)
    private val stoppedAt = AtomicReference<Instant?>(null)
    private val exitCode = AtomicReference<Int?>(null)
    private val message = AtomicReference("idle")
    private val logBuffer: ArrayDeque<String> = ArrayDeque()
    private val lock = Any()

    private companion object {
        private const val ORPHAN_SETTLE_MS = 800L
    }

    private val maxLogLines = 500
    private val configuredPort = AtomicReference(7456)

    override fun start(config: McpServerConfig): Status {
        val running = process.get()?.isAlive == true
        if (running) {
            appendLog("[info] start ignored – already running (pid=${pidRef.get()})")
            return status()
        }
        val script = scriptLocator.locateScript()
        if (script == null) {
            message.set("mcp-server/dist/index.js not found – run `npm run build` in mcp-server/")
            appendLog("[error] ${message.get()}")
            return status()
        }
        val node = nodeLocator.locateNode()
        if (node == null) {
            message.set("`node` >= 20 not found — install Node.js 20+ (https://nodejs.org)")
            appendLog("[error] ${message.get()}")
            return status()
        }
        val port = portFromBaseUrl(s(config.baseUrl))
        configuredPort.set(port)
        if (portProbe.isInUse("127.0.0.1", port, 250)) {
            appendLog("[info] port $port in use – attempting to kill orphaned process before start")
            orphanKiller.killProcessOnPort(port)
            sleeper.sleep(ORPHAN_SETTLE_MS)
        }
        if (portProbe.isInUse("127.0.0.1", port, 250)) {
            message.set("port $port already in use – another MCP server (e.g. Docker) seems to be running")
            appendLog("[error] ${message.get()}")
            return status()
        }
        try {
            val envMap =
                buildEnvMap(
                    port = port,
                    config = config,
                )
            val proc =
                spawner.spawn(
                    command = listOf(node, script.absolutePath),
                    workingDir = script.parentFile?.parentFile,
                    environment = envMap,
                )
            process.set(proc)
            pidRef.set(proc.pid())
            startedAt.set(Instant.now())
            stoppedAt.set(null)
            exitCode.set(null)
            message.set("running")
            appendLog("[info] started node ${script.absolutePath} on port $port (pid=${proc.pid()})")
            Thread({ pumpStdout(proc) }, "mcp-stdout").apply { isDaemon = true }.start()
            Thread({ watchExit(proc) }, "mcp-watch").apply { isDaemon = true }.start()
        } catch (e: Exception) {
            message.set("failed to start: ${e.message}")
            appendLog("[error] ${message.get()}")
            log.warn("[McpProcess] start failed", e)
        }
        return status()
    }

    override fun stop(): Status {
        val proc = process.get()
        if (proc == null || !proc.isAlive) {
            val port = configuredPort.get()
            if (portProbe.isInUse("127.0.0.1", port, 250)) {
                appendLog("[info] managed process not tracked but port $port in use – killing orphaned process")
                orphanKiller.killProcessOnPort(port)
                stoppedAt.set(Instant.now())
                message.set("orphaned process on port $port killed")
            } else {
                appendLog("[info] stop ignored – not running")
            }
            return status()
        }
        appendLog("[info] stopping pid=${pidRef.get()} ...")
        proc.destroy()
        try {
            if (!proc.waitFor(5, TimeUnit.SECONDS)) {
                appendLog("[warn] graceful stop timed out, sending SIGKILL")
                proc.destroyForcibly()
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            appendLog("[warn] interrupted while waiting for stop — forcing SIGKILL")
            proc.destroyForcibly()
        }
        return status()
    }

    @PreDestroy
    fun shutdown() {
        quietly { stop() }
    }

    override fun status(): Status =
        synchronized(lock) {
            val proc = process.get()
            val running = proc?.isAlive == true
            Status(
                running = running,
                pid = if (running) pidRef.get() else null,
                startedAt = startedAt.get(),
                stoppedAt = stoppedAt.get(),
                exitCode = exitCode.get(),
                message = message.get(),
                logs = logBuffer.toList(),
            )
        }

    private fun pumpStdout(proc: Process) {
        BufferedReader(InputStreamReader(proc.inputStream)).closingAuto { r ->
            try {
                var line: String? = r.readLine()
                while (line != null) {
                    appendLog(line)
                    line = r.readLine()
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun watchExit(proc: Process) {
        try {
            val code = proc.waitFor()
            appendLog("[info] process ended (exit=$code)")
            if (process.get() === proc) {
                exitCode.set(code)
                stoppedAt.set(Instant.now())
                message.set(if (code == 0) "exited cleanly" else "exited with code $code")
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            appendLog("[warn] watcher interrupted: ${e.message}")
        }
    }

    private fun appendLog(line: String) {
        synchronized(lock) {
            val ts = Instant.now().toString()
            logBuffer.addLast("$ts  $line")
            while (logBuffer.size > maxLogLines) logBuffer.removeFirst()
        }
    }

    private fun buildEnvMap(
        port: Int,
        config: McpServerConfig,
    ): Map<String, String> {
        val map = mutableMapOf<String, String>()
        map["MCP_PORT"] = port.toString()
        map["MCP_HOST"] = "127.0.0.1"
        putIfNotBlank(map, "MCP_AUTH_TOKEN", config.authToken)
        putIfNotBlank(map, "JIRA_BASE_URL", config.jiraBaseUrl)
        putIfNotBlank(map, "JIRA_USERNAME", config.jiraUsername)
        putIfNotBlank(map, "JIRA_API_TOKEN", config.jiraApiToken)
        putIfNotBlank(map, "CONFLUENCE_BASE_URL", config.confluenceBaseUrl)
        putIfNotBlank(map, "CONFLUENCE_USERNAME", config.confluenceUsername)
        putIfNotBlank(map, "CONFLUENCE_API_TOKEN", config.confluenceApiToken)
        putIfNotBlank(map, "GITLAB_BASE_URL", config.gitlabBaseUrl)
        putIfNotBlank(map, "GITLAB_ACCESS_TOKEN", config.gitlabAccessToken)
        putIfNotBlank(map, "GITHUB_TOKEN", config.githubToken)
        putIfNotBlank(map, "GITHUB_API_BASE", config.githubApiBase)
        putIfNotBlank(map, "GRAFANA_BASE_URL", config.grafanaBaseUrl)
        putIfNotBlank(map, "GRAFANA_API_KEY", config.grafanaApiKey)
        return map
    }

    private fun portFromBaseUrl(baseUrl: String): Int =
        runCatching {
            val u = URI(baseUrl.ifBlank { "http://localhost:7456" })
            if (u.port > 0) u.port else 7456
        }.getOrDefault(7456)

    private fun s(value: String?): String = value.orEmpty()

    private fun putIfNotBlank(
        env: MutableMap<String, String>,
        key: String,
        value: String?,
    ) {
        val v = s(value)
        if (v.isNotBlank()) env[key] = v
    }
}
