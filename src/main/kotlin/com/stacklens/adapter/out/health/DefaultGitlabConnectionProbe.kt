package com.stacklens.adapter.out.health

import com.stacklens.application.port.out.GitlabConnectionTestPort
import com.stacklens.application.port.out.health.ConnectionProbeResult
import com.stacklens.application.port.out.health.GitlabConnectionProbePort
import com.stacklens.domain.model.GitlabConfig
import org.springframework.stereotype.Component

@Component
class DefaultGitlabConnectionProbe(
    private val delegate: GitlabConnectionTestPort,
) : GitlabConnectionProbePort {
    override fun probe(config: GitlabConfig): ConnectionProbeResult =
        when (val r = delegate.testConnection(config)) {
            is GitlabConnectionTestPort.TestResult.Success -> ConnectionProbeResult.Ok()
            is GitlabConnectionTestPort.TestResult.Failure -> ConnectionProbeResult.Failure(truncateMessage(r.payload["message"]?.toString().orEmpty()))
        }
}
