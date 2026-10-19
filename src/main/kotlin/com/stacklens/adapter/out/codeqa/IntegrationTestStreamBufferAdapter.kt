package com.stacklens.adapter.out.codeqa

import com.stacklens.adapter.out.codeqa.IntegrationTestStream
import com.stacklens.adapter.out.codeqa.IntegrationTestStreamPlan
import com.stacklens.application.port.out.JsonWriterPort
import com.stacklens.application.port.out.platform.HostDirectoryPort
import com.stacklens.application.port.out.platform.SystemInfoPort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.domain.model.CodeQaAnalysis
import org.springframework.stereotype.Component
import java.time.format.DateTimeFormatter

@Component
class IntegrationTestStreamBufferAdapter(
    private val clock: ClockPort,
    private val systemInfo: SystemInfoPort,
    private val hostDirectories: HostDirectoryPort,
    private val jsonWriter: JsonWriterPort,
) : IntegrationTestStream {
    override fun header() = "╔══════════════════════════════════════╗\n║ StackLens – Integration Tests        ║\n╚══════════════════════════════════════╝\n\n"

    override fun environmentBanner(): String {
        val host = systemInfo.hostSystem()
        val tempRoot = hostDirectories.temporaryDirectory().trimEnd('/')
        return buildString {
            append("🖥️  Host: ${host.osName} ${host.osVersion} (${host.osArch})\n")
            append("☕  Java: ${host.javaVersion}\n")
            append("📂  Temp root: $tempRoot\n")
        }
    }

    override fun emit(
        sb: StringBuilder,
        text: String,
    ) {
        synchronized(sb) { sb.append(text) }
    }

    override fun emitStarted(
        sb: StringBuilder,
        branch: String,
        targetBranch: String,
        changedFiles: Int,
        tmpDir: String,
        os: String,
    ) {
        emitStatus(
            sb,
            linkedMapOf(
                "type" to "started",
                "branch" to branch,
                "targetBranch" to targetBranch,
                "changedFiles" to changedFiles,
                "tmpDir" to tmpDir,
                "os" to os,
            ),
        )
    }

    override fun emitStep(
        sb: StringBuilder,
        index: Int,
        name: String,
        state: String,
        detail: String?,
    ) {
        emitStatus(
            sb,
            linkedMapOf(
                "type" to "step",
                "index" to index,
                "name" to name,
                "state" to state,
                "detail" to detail,
            ),
        )
    }

    override fun emitPlan(
        sb: StringBuilder,
        plan: IntegrationTestStreamPlan,
    ) {
        emitStatus(
            sb,
            linkedMapOf(
                "type" to "services",
                "services" to plan.services,
                "testCommands" to plan.testCommands,
                "projectTypes" to plan.projectTypes,
                "testStrategy" to plan.testStrategy,
                "testTargets" to plan.testTargets,
                "reasoning" to plan.reasoning,
                "waitSeconds" to plan.waitSeconds,
            ),
        )
    }

    override fun emitWaiting(
        sb: StringBuilder,
        seconds: Int,
        services: List<String>,
    ) {
        emitStatus(
            sb,
            linkedMapOf(
                "type" to "waiting",
                "seconds" to seconds,
                "services" to services,
            ),
        )
    }

    override fun emitService(
        sb: StringBuilder,
        name: String,
        state: String,
    ) {
        emitStatus(
            sb,
            linkedMapOf(
                "type" to "service",
                "name" to name,
                "state" to state,
            ),
        )
    }

    override fun emitTestsRunning(
        sb: StringBuilder,
        commands: List<String>,
    ) {
        emitStatus(
            sb,
            linkedMapOf(
                "type" to "tests",
                "state" to "running",
                "commands" to
                    commands.mapIndexed { idx, cmd ->
                        linkedMapOf("index" to idx, "command" to cmd, "state" to "running")
                    },
            ),
        )
    }

    override fun emitTestRunning(
        sb: StringBuilder,
        index: Int,
        command: String,
    ) {
        emitStatus(
            sb,
            linkedMapOf(
                "type" to "test",
                "index" to index,
                "command" to command,
                "state" to "running",
            ),
        )
    }

    override fun emitTestFinished(
        sb: StringBuilder,
        index: Int,
        command: String,
        exitCode: Int,
    ) {
        emitStatus(
            sb,
            linkedMapOf(
                "type" to "test",
                "index" to index,
                "command" to command,
                "state" to if (exitCode == 0) "passed" else "failed",
                "exitCode" to exitCode,
            ),
        )
    }

    override fun emitTestsSummary(
        sb: StringBuilder,
        passed: Int,
        total: Int,
        results: List<Pair<String, Boolean>>,
    ) {
        emitStatus(
            sb,
            linkedMapOf(
                "type" to "tests",
                "state" to "done",
                "passed" to passed,
                "total" to total,
                "results" to results,
            ),
        )
    }

    override fun emitError(
        sb: StringBuilder,
        message: String,
    ) {
        emitStatus(
            sb,
            linkedMapOf(
                "type" to "error",
                "message" to message,
            ),
        )
    }

    override fun appendTestLog(
        analysis: CodeQaAnalysis,
        message: String,
    ) {
        val stamp = clock.localDateTime().format(TIME_FORMAT)
        analysis.integrationTestLog = "${analysis.integrationTestLog.orEmpty()}[$stamp] $message\n"
    }

    private fun emitStatus(
        sb: StringBuilder,
        payload: Map<String, Any?>,
    ) {
        val merged = LinkedHashMap<String, Any?>().apply { put("ts", clock.epochMillis()) }
        merged.putAll(payload)
        val json = jsonWriter.encode(merged)
        synchronized(sb) { sb.append("[STATUS]").append(json).append("[/STATUS]\n") }
    }

    private companion object {
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    }
}
