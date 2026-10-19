package com.stacklens.application.port.out.platform

interface DemoRuntimeHostPort {
    fun alignedServiceUrl(url: String): String

    fun alignedMcpServerUrl(url: String): String

    fun alignedHost(host: String): String

    fun serviceHost(): String

    fun mcpServiceBaseUrl(): String
}
