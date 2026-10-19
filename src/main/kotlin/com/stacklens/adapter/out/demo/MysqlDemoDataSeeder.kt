package com.stacklens.adapter.out.demo

import com.stacklens.application.port.out.DatabaseConfigPort
import com.stacklens.application.port.out.platform.DemoRuntimeHostPort
import com.stacklens.domain.enums.DatabaseType
import com.stacklens.domain.model.DatabaseConfig
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class MysqlDemoDataSeeder(
    private val databaseConfigRepo: DatabaseConfigPort,
    private val hostResolver: DemoRuntimeHostPort,
) : DemoDataSeederStrategy {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun seed() {
        val host = hostResolver.serviceHost()
        listOf("ecommerce", "auth_db", "analytics_db").forEach { dbName ->
            val demoName = "Demo MySQL – $dbName"
            val existing = databaseConfigRepo.findAllDatabaseConfigs().find { it.name == demoName }
            when {
                existing == null -> {
                    databaseConfigRepo.saveDatabaseConfig(
                        DatabaseConfig(
                            name = demoName,
                            dbType = DatabaseType.MYSQL,
                            host = host,
                            port = 3306,
                            databaseName = dbName,
                            username = "stacklens",
                            password = "stacklens",
                            active = true,
                        ),
                    )
                    log.info("[DemoDataSeeder]  + MySQL  → {}:3306 ({})", host, demoName)
                }
                existing.host != host -> {
                    existing.host = host
                    databaseConfigRepo.saveDatabaseConfig(existing)
                    log.info(
                        "[DemoDataSeeder]  ↻ MySQL host refreshed for {}: → {}",
                        demoName,
                        host,
                    )
                }
            }
        }
    }
}
