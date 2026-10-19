package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.config.TextFilterSettingsUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/text-filter")
open class TextFilterConfigController(
    private val settings: TextFilterSettingsUseCase,
) {
    @GetMapping("/config")
    fun getConfig(): ResponseEntity<Map<String, Any>> = ResponseEntity.ok(mapOf("enabled" to settings.isTextFilterEnabled()))

    @PutMapping("/config")
    fun setConfig(
        @RequestBody body: Map<String, Any>,
    ): ResponseEntity<Map<String, Any>> {
        val raw = body["enabled"]
        val enabled =
            when (raw) {
                is Boolean -> raw
                is String -> raw.equals("true", ignoreCase = true)
                else ->
                    return ResponseEntity
                        .badRequest()
                        .body(mapOf("error" to "Field 'enabled' must be boolean or \"true\"/\"false\""))
            }
        return ResponseEntity.ok(mapOf("ok" to true, "enabled" to settings.setTextFilterEnabled(enabled)))
    }
}
