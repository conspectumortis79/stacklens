package com.stacklens.adapter.out.demo

import com.stacklens.application.port.out.platform.DemoGrafanaEndpointPort
import com.stacklens.application.port.out.platform.DemoRuntimeHostPort
import org.springframework.stereotype.Component
import java.net.URI

@Component
class DemoGrafanaEndpointResolver(
    private val runtimeHosts: DemoRuntimeHostPort,
    private val envReader: (String) -> String? = { System.getenv(it) },
) : DemoGrafanaEndpointPort {
    override fun baseUrl(): String =
        runtimeHosts.alignedServiceUrl(
            "http://${DemoHostResolver.LOCAL_HOST}:${configuredPort()}",
        )

    override fun alignedUrl(url: String): String {
        val hostAligned = runtimeHosts.alignedServiceUrl(url)
        val uri = parse(hostAligned) ?: return hostAligned
        val host = demoHost(uri) ?: return hostAligned
        val port = configuredPort()
        if (uri.port == port || (uri.port == NO_EXPLICIT_PORT && port == DEFAULT_GRAFANA_DEMO_PORT)) return hostAligned
        return "${uri.scheme}://$host:$port"
    }

    private fun configuredPort(): Int {
        val configured = envReader(GRAFANA_DEMO_PORT_ENV)?.trim()
        return configured?.toIntOrNull()?.takeIf { it in VALID_PORT_RANGE } ?: DEFAULT_GRAFANA_DEMO_PORT
    }

    private fun demoHost(uri: URI): String? {
        val host = uri.host ?: return null
        return host.lowercase().takeIf { it in DEMO_HOSTS }
    }

    private fun parse(url: String): URI? = runCatching { URI(url) }.getOrNull()

    companion object {
        const val GRAFANA_DEMO_PORT_ENV = "GRAFANA_DEMO_PORT"
        const val DEFAULT_GRAFANA_DEMO_PORT = 3000

        private const val NO_EXPLICIT_PORT = -1
        private val VALID_PORT_RANGE = 1..65535
        private val DEMO_HOSTS = listOf(DemoHostResolver.LOCAL_HOST, DemoHostResolver.DOCKER_HOST)
    }
}
