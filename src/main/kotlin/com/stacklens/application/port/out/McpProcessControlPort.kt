package com.stacklens.application.port.out

import com.stacklens.domain.model.McpServerConfig
import java.time.Instant

interface McpProcessControlPort {
    data class Status(
        val running: Boolean,
        val pid: Long?,
        val startedAt: Instant?,
        val stoppedAt: Instant?,
        val exitCode: Int?,
        val message: String,
        val logs: List<String>,
    )

    fun start(config: McpServerConfig): Status

    fun stop(): Status

    fun status(): Status
}
