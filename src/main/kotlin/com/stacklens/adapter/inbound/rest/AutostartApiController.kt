package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.platform.AutostartChange
import com.stacklens.application.port.inbound.platform.AutostartUseCase
import com.stacklens.domain.autostart.AutostartMode
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/autostart")
open class AutostartApiController(
    private val useCase: AutostartUseCase,
) {
    data class EnableReq(
        val mode: String? = null,
    )

    @GetMapping("/status")
    fun getStatus(): ResponseEntity<Map<String, Any>> =
        with(useCase.status()) {
            ResponseEntity.ok(
                mapOf(
                    "supported" to supported,
                    "enabled" to enabled,
                    "platform" to platform,
                    "mode" to mode.wire,
                    "serverPort" to serverPort,
                ),
            )
        }

    @PostMapping("/enable")
    fun enable(
        @RequestBody(required = false) req: EnableReq?,
    ): ResponseEntity<Map<String, Any>> = render(useCase.enable(AutostartMode.fromWire(req?.mode)), withMode = true)

    @PostMapping("/disable")
    fun disable(): ResponseEntity<Map<String, Any>> = render(useCase.disable(), withMode = false)

    private fun render(
        change: AutostartChange,
        withMode: Boolean,
    ): ResponseEntity<Map<String, Any>> =
        when (change) {
            is AutostartChange.Applied ->
                ResponseEntity.ok(
                    if (withMode) {
                        mapOf("ok" to true, "mode" to change.mode.wire)
                    } else {
                        mapOf("ok" to true)
                    },
                )

            is AutostartChange.Unsupported -> ResponseEntity.ok(mapOf("ok" to false, "error" to change.reason))

            is AutostartChange.Rejected ->
                ResponseEntity
                    .internalServerError()
                    .body(mapOf("ok" to false, "error" to change.reason))
        }
}
