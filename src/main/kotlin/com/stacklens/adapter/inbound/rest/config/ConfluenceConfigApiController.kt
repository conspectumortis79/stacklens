
package com.stacklens.adapter.inbound.rest.config

import com.stacklens.adapter.inbound.rest.ConfluenceConfigDto
import com.stacklens.application.port.inbound.config.ConfluenceConfigUseCase
import com.stacklens.domain.model.ConfluenceConfig
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/config")
class ConfluenceConfigApiController(
    private val configManagement: ConfluenceConfigUseCase,
) {
    @GetMapping("/confluence")
    fun listConfluenceConfigs(): List<ConfluenceConfigDto> = configManagement.listVisibleConfluenceConfigs().map { it.toDto() }

    @PostMapping("/confluence")
    fun saveConfluenceConfig(
        @RequestBody dto: ConfluenceConfigDto,
    ): ConfluenceConfigDto =
        loadMutateSave(
            id = dto.id,
            find = configManagement::findConfluenceConfig,
            create = { ConfluenceConfig(name = "", baseUrl = "", username = "", apiToken = "") },
            secrets = listOf(dto.apiToken to ConfluenceConfig::apiToken),
            save = configManagement::saveConfluenceConfig,
        ) {
            name = dto.name
            baseUrl = dto.baseUrl
            username = dto.username
            spaceKeys = dto.spaceKeys
            cloudMode = dto.cloudMode
            searchRootUrl = dto.searchRootUrl
            active = dto.active
        }.toDto()

    @DeleteMapping("/confluence/{id}")
    fun deleteConfluenceConfig(
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        if (configManagement.findConfluenceConfig(id) == null) return ResponseEntity.notFound().build()
        configManagement.deleteConfluenceConfig(id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/confluence/{id}/test")
    fun testConfluenceConnection(
        @PathVariable id: Long,
        @RequestParam(required = false) keyword: String?,
    ): ResponseEntity<Map<String, Any>> {
        val payload = configManagement.testConfluenceConnection(id, keyword) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(payload)
    }
}
