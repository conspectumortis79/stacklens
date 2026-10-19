package com.stacklens.application.service.config

import com.stacklens.application.port.inbound.config.DatabaseConfigUseCase
import com.stacklens.application.port.out.DatabaseConfigPort
import com.stacklens.application.port.out.DatabaseConnectionTestPort
import com.stacklens.application.port.out.config.DatabaseConfigReadPort
import com.stacklens.application.port.out.platform.DemoConfigPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.DatabaseConfig

@ApplicationService
class DatabaseConfigService(
    private val dbConfigRepo: DatabaseConfigPort,
    private val databaseConnectionTest: DatabaseConnectionTestPort,
    private val demoConfigFilter: DemoConfigPort,
) : DatabaseConfigUseCase,
    DatabaseConfigReadPort {
    override fun listDatabaseConfigs(): List<DatabaseConfig> = dbConfigRepo.findAllDatabaseConfigs()

    override fun listVisibleDatabaseConfigs(): List<DatabaseConfig> = demoConfigFilter.apply(listDatabaseConfigs()) { it.name }

    override fun findDatabaseConfig(id: Long): DatabaseConfig? = dbConfigRepo.findDatabaseConfigById(id)

    override fun saveDatabaseConfig(config: DatabaseConfig): DatabaseConfig = dbConfigRepo.saveDatabaseConfig(config)

    override fun deleteDatabaseConfig(id: Long) = dbConfigRepo.deleteDatabaseConfigById(id)

    override fun deleteAllDatabaseConfigs() = dbConfigRepo.deleteAllDatabaseConfigs()

    override fun testDatabaseConnection(id: Long): Map<String, Any>? = dbConfigRepo.findDatabaseConfigById(id)?.let { databaseConnectionTest.testConnection(it).payload }
}
