package com.stacklens.adapter.inbound.rest.config

import com.stacklens.adapter.inbound.rest.DatabaseConfigDto
import com.stacklens.application.port.inbound.config.DatabaseConfigUseCase
import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.domain.enums.DatabaseType
import com.stacklens.domain.model.DatabaseConfig
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
class DatabaseConfigApiController(
    private val configManagement: DatabaseConfigUseCase,
    private val i18n: I18nUseCase,
) {
    @GetMapping("/database")
    fun listDbConfigs(): List<DatabaseConfigDto> = configManagement.listVisibleDatabaseConfigs().map { it.toDto() }

    @PostMapping("/database")
    fun saveDbConfig(
        @RequestBody dto: DatabaseConfigDto,
    ): ResponseEntity<Any> {
        val parsedDbType =
            try {
                DatabaseType.valueOf(dto.dbType)
            } catch (e: IllegalArgumentException) {
                return ResponseEntity.badRequest().body(mapOf("error" to i18n.t("config.be_invalid_db_type", dto.dbType)))
            }
        return ResponseEntity.ok(
            loadMutateSave(
                id = dto.id,
                find = configManagement::findDatabaseConfig,
                create = {
                    DatabaseConfig(
                        name = dto.name,
                        dbType = parsedDbType,
                        host = dto.host,
                        port = dto.port,
                        databaseName = dto.databaseName,
                        username = dto.username,
                        password = dto.password,
                    )
                },
                secrets = listOf(dto.password to DatabaseConfig::password),
                save = configManagement::saveDatabaseConfig,
            ) {
                name = dto.name
                dbType = parsedDbType
                host = dto.host
                port = dto.port
                databaseName = dto.databaseName
                username = dto.username
                schemas = dto.schemas
                active = dto.active
            }.toDto(),
        )
    }

    @DeleteMapping("/database/{id}")
    fun deleteDbConfig(
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        if (configManagement.findDatabaseConfig(id) == null) return ResponseEntity.notFound().build()
        configManagement.deleteDatabaseConfig(id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/database/{id}/test")
    fun testDbConnection(
        @PathVariable id: Long,
    ): ResponseEntity<Map<String, Any>> = configManagement.testDatabaseConnection(id)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()

    @GetMapping("/database/types")
    fun dbTypes(): List<Map<String, Any>> = DatabaseType.entries.map { mapOf("name" to it.name, "defaultPort" to it.defaultPort) }
}
