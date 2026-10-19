package com.stacklens.adapter.out.health

import com.stacklens.application.port.out.ConfluenceConnectionTestPort
import com.stacklens.application.port.out.health.ConfluenceConnectionProbePort
import com.stacklens.application.port.out.health.ConnectionProbeResult
import com.stacklens.domain.model.ConfluenceConfig
import org.springframework.stereotype.Component

@Component
class DefaultConfluenceConnectionProbe(
    private val delegate: ConfluenceConnectionTestPort,
) : ConfluenceConnectionProbePort {
    override fun probe(config: ConfluenceConfig): ConnectionProbeResult =
        when (val r = delegate.testConnection(config, null)) {
            is ConfluenceConnectionTestPort.TestResult.Success -> ConnectionProbeResult.Ok()
            is ConfluenceConnectionTestPort.TestResult.Failure -> ConnectionProbeResult.Failure(truncateMessage(r.payload["message"]?.toString().orEmpty()))
        }
}
