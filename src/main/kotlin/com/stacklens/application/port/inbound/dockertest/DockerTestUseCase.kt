package com.stacklens.application.port.inbound.dockertest

data class DockerTestInfoView(
    val composeFile: String,
    val found: Boolean,
    val dockerAvailable: Boolean,
    val dockerDiagnostic: String,
)

data class DockerTestStatusView(
    val phase: String,
    val jobActive: Boolean,
    val available: Boolean,
    val containers: Int,
    val running: Int,
)

data class DockerTestRestartView(
    val ok: Boolean,
    val dockerAvailable: Boolean,
    val dockerDiagnostic: String,
    val message: String,
    val details: String,
)

data class DockerTestJobClearedView(
    val message: String,
)

data class DockerTestStateView(
    val jobId: String?,
    val status: String,
    val output: List<String>,
    val lineCount: Int,
)

interface DockerTestUseCase {
    fun info(): DockerTestInfoView

    fun status(): DockerTestStatusView

    fun clearCurrentJob(): DockerTestJobClearedView

    fun restartDocker(): DockerTestRestartView

    fun state(): DockerTestStateView

    fun startCompose(sink: DockerTestEventSink)

    fun attachOutput(
        since: Int,
        sink: DockerTestEventSink,
    )

    fun resetDocker(sink: DockerTestEventSink)
}
