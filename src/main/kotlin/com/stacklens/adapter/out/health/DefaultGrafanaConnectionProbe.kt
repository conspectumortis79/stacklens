package com.stacklens.adapter.out.health

import com.stacklens.application.port.out.GrafanaConnectionTestPort
import com.stacklens.application.port.out.health.ConnectionProbeResult
import com.stacklens.application.port.out.health.GrafanaConnectionProbePort
import com.stacklens.domain.model.GrafanaConfig
import org.springframework.stereotype.Component

@Component
class DefaultGrafanaConnectionProbe(
    private val delegate: GrafanaConnectionTestPort,
) : GrafanaConnectionProbePort {
    override fun probe(config: GrafanaConfig): ConnectionProbeResult =
        when (val r = delegate.testConnection(config)) {
            is GrafanaConnectionTestPort.TestResult.Success -> ConnectionProbeResult.Ok()
            is GrafanaConnectionTestPort.TestResult.Failure -> ConnectionProbeResult.Failure(truncateMessage(r.payload["message"]?.toString().orEmpty()))
        }
}
