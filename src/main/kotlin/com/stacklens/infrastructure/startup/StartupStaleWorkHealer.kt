package com.stacklens.infrastructure.startup

import com.stacklens.application.port.inbound.startup.StartupMaintenanceUseCase
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.stereotype.Component

@Component
class StartupStaleWorkHealer(
    private val startupMaintenance: StartupMaintenanceUseCase,
) : SmartInitializingSingleton {
    override fun afterSingletonsInstantiated() {
        startupMaintenance.healStaleWork()
    }
}
