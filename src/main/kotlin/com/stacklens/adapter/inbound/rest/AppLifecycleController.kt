package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.platform.AppLifecycleUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
open class AppLifecycleController(
    private val appLifecycle: AppLifecycleUseCase,
) {
    @PostMapping("/quit")
    fun quitApp(): ResponseEntity<Map<String, Any>> {
        scheduleShutdown()
        return ResponseEntity.ok(mapOf("status" to "shutting down"))
    }

    internal open fun scheduleShutdown() {
        Thread(appLifecycle::quit, "stacklens-app-quit").apply {
            isDaemon = true
            start()
        }
    }
}
