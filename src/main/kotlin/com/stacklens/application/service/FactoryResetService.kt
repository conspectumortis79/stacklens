package com.stacklens.application.service

import com.stacklens.application.port.inbound.platform.FactoryResetUseCase
import com.stacklens.application.port.out.transaction.TransactionPort
import com.stacklens.application.stereotype.ApplicationService

@ApplicationService
class FactoryResetService(
    private val resetHandlers: List<FactoryResetHandler>,
    private val tx: TransactionPort,
) : FactoryResetUseCase {
    override fun factoryReset() {
        tx.inTransaction { resetHandlers.forEach { it.reset() } }
    }
}
