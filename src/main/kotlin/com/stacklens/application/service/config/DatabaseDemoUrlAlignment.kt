package com.stacklens.application.service.config

import com.stacklens.application.port.out.DatabaseConfigPort
import com.stacklens.application.stereotype.ApplicationService

@ApplicationService
class DatabaseDemoUrlAlignment(
    private val databaseConfigRepo: DatabaseConfigPort,
    private val aligner: DemoUrlAligner,
) : DemoUrlAlignmentSection {
    override val label: String = "Database"

    override fun align(): List<String> =
        aligner.alignHosts(
            entries = databaseConfigRepo.findAllDatabaseConfigs(),
            hostOf = { it.host },
            hostSetter = { config, host -> config.host = host },
            persist = { databaseConfigRepo.saveDatabaseConfig(it) },
        )
}
