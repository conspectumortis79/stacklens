package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.platform.FactoryResetUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin")
class AdminController(
    private val factoryResetService: FactoryResetUseCase,
) {
    @PostMapping("/factory-reset-db")
    fun factoryResetDb(): ResponseEntity<Map<String, Any>> {
        factoryResetService.factoryReset()
        return ResponseEntity.ok(mapOf("success" to true))
    }
}
