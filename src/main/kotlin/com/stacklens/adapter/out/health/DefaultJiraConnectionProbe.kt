package com.stacklens.adapter.out.health

import com.stacklens.application.port.out.JiraConnectionTestPort
import com.stacklens.application.port.out.health.ConnectionProbeResult
import com.stacklens.application.port.out.health.JiraConnectionProbePort
import com.stacklens.domain.model.JiraConfig
import org.springframework.stereotype.Component

@Component
class DefaultJiraConnectionProbe(
    private val delegate: JiraConnectionTestPort,
) : JiraConnectionProbePort {
    override fun probe(config: JiraConfig): ConnectionProbeResult =
        when (val r = delegate.testConnection(config, null)) {
            is JiraConnectionTestPort.TestResult.Success -> ConnectionProbeResult.Ok()
            is JiraConnectionTestPort.TestResult.Failure -> ConnectionProbeResult.Failure(extractMessage(r))
        }

    private fun extractMessage(failure: JiraConnectionTestPort.TestResult.Failure): String {
        val raw = failure.payload["message"]?.toString() ?: ""
        return truncateMessage(raw)
    }
}
