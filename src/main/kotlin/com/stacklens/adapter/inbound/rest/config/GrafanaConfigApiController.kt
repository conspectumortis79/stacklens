
package com.stacklens.adapter.inbound.rest.config

import com.stacklens.adapter.inbound.rest.GrafanaConfigDto
import com.stacklens.application.port.inbound.config.GrafanaConfigUseCase
import com.stacklens.domain.model.GrafanaConfig
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/config")
class GrafanaConfigApiController(
    private val configManagement: GrafanaConfigUseCase,
) {
    @GetMapping("/grafana")
    fun listGrafanaConfigs(): List<GrafanaConfigDto> = configManagement.listVisibleGrafanaConfigs().map { it.toDto() }

    @PostMapping("/grafana")
    fun saveGrafanaConfig(
        @RequestBody dto: GrafanaConfigDto,
    ): GrafanaConfigDto =
        loadMutateSave(
            id = dto.id,
            find = configManagement::findGrafanaConfig,
            create = { GrafanaConfig(name = "", baseUrl = "", username = "", password = "") },
            secrets =
                listOf(
                    dto.password to GrafanaConfig::password,
                    dto.apiToken to GrafanaConfig::apiToken,
                ),
            save = configManagement::saveGrafanaConfig,
        ) {
            name = dto.name
            baseUrl = dto.baseUrl
            username = dto.username
            orgId = dto.orgId
            scanPanelQueries = dto.scanPanelQueries
            active = dto.active
        }.toDto()

    @DeleteMapping("/grafana/{id}")
    fun deleteGrafanaConfig(
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        if (configManagement.findGrafanaConfig(id) == null) return ResponseEntity.notFound().build()
        configManagement.deleteGrafanaConfig(id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/grafana/{id}/test")
    fun testGrafanaConnection(
        @PathVariable id: Long,
    ): ResponseEntity<Map<String, Any>> {
        val payload = configManagement.testGrafanaConnection(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(payload)
    }
}
