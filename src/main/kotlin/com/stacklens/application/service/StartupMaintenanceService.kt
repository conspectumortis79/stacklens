package com.stacklens.application.service

import com.stacklens.application.port.inbound.startup.StartupMaintenanceUseCase
import com.stacklens.application.service.startup.StaleWorkHealer
import com.stacklens.application.stereotype.ApplicationService

@ApplicationService
class StartupMaintenanceService(
    private val healers: List<StaleWorkHealer>,
) : StartupMaintenanceUseCase {
    override fun healStaleWork() {
        healers.forEach { it.heal() }
    }
}
