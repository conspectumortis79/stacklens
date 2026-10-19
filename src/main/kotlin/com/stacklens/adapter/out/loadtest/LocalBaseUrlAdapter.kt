package com.stacklens.adapter.out.loadtest

import com.stacklens.application.port.out.loadtest.LocalBaseUrlPort
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class LocalBaseUrlAdapter : LocalBaseUrlPort {
    @Value("\${server.port:8090}")
    private val serverPort: Int = 8090

    override fun currentBaseUrl(): String = "http://localhost:$serverPort"
}
