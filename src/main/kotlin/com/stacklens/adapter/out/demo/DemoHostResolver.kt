package com.stacklens.adapter.out.demo

import com.stacklens.application.port.out.platform.DemoRuntimeHostPort
import org.springframework.stereotype.Component

@Component
class DemoHostResolver : DemoRuntimeHostPort {
    fun hostFor(
        dockerHost: String = DOCKER_HOST,
        envValue: String? = DemoRuntimeMode.envValue(),
        dockerFlag: Boolean = DemoRuntimeMode.dockerFlag(),
    ): String {
        val insideContainer = envValue == "true" || dockerFlag
        return if (insideContainer) dockerHost else LOCAL_HOST
    }

    fun mcpHostFor(
        envValue: String? = DemoRuntimeMode.envValue(),
        dockerFlag: Boolean = DemoRuntimeMode.dockerFlag(),
    ): String {
        val insideContainer = envValue == "true" || dockerFlag
        return if (insideContainer) MCP_DOCKER_HOST else LOCAL_HOST
    }

    fun mcpBaseUrlFor(
        port: Int = MCP_DEFAULT_PORT,
        envValue: String? = DemoRuntimeMode.envValue(),
        dockerFlag: Boolean = DemoRuntimeMode.dockerFlag(),
    ): String = "http://${mcpHostFor(envValue, dockerFlag)}:$port"

    fun refreshHost(
        url: String,
        envValue: String? = DemoRuntimeMode.envValue(),
        dockerFlag: Boolean = DemoRuntimeMode.dockerFlag(),
    ): String {
        val expected = hostFor(DOCKER_HOST, envValue, dockerFlag)
        val current = extractHost(url) ?: return url
        val isOtherDefault =
            (expected == DOCKER_HOST && current == LOCAL_HOST) ||
                (expected == LOCAL_HOST && current == DOCKER_HOST)
        return if (isOtherDefault) url.replace("://$current", "://$expected") else url
    }

    fun refreshMcpBaseUrl(
        url: String,
        envValue: String? = DemoRuntimeMode.envValue(),
        dockerFlag: Boolean = DemoRuntimeMode.dockerFlag(),
    ): String {
        val current = extractHost(url) ?: return url
        val expectedHost = mcpHostFor(envValue, dockerFlag)
        val isOtherDefault =
            (expectedHost == LOCAL_HOST && current == MCP_DOCKER_HOST) ||
                (expectedHost == MCP_DOCKER_HOST && current == LOCAL_HOST)
        return if (isOtherDefault) url.replace("://$current", "://$expectedHost") else url
    }

    override fun alignedServiceUrl(url: String): String = refreshHost(url)

    override fun alignedMcpServerUrl(url: String): String = refreshMcpBaseUrl(url)

    override fun serviceHost(): String = hostFor()

    override fun mcpServiceBaseUrl(): String = mcpBaseUrlFor()

    override fun alignedHost(host: String): String =
        when (host) {
            LOCAL_HOST, DOCKER_HOST -> hostFor()
            else -> host
        }

    private fun extractHost(url: String): String? =
        try {
            val u = java.net.URI(url)
            u.host
        } catch (_: Exception) {
            null
        }

    companion object {
        const val DOCKER_HOST = "host.docker.internal"
        const val LOCAL_HOST = "localhost"
        const val MCP_DOCKER_HOST = "stacklens-mcp"
        const val MCP_DEFAULT_PORT = 7456
    }
}
