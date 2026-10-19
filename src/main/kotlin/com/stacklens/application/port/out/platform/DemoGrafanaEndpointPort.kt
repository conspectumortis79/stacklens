package com.stacklens.application.port.out.platform

interface DemoGrafanaEndpointPort {
    fun baseUrl(): String

    fun alignedUrl(url: String): String
}
