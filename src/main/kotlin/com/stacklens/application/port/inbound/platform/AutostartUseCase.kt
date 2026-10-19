package com.stacklens.application.port.inbound.platform

import com.stacklens.domain.autostart.AutostartMode

data class AutostartStatus(
    val platform: String,
    val supported: Boolean,
    val enabled: Boolean,
    val mode: AutostartMode,
    val serverPort: Int,
)

sealed interface AutostartChange {
    data class Applied(
        val mode: AutostartMode,
    ) : AutostartChange

    data class Unsupported(
        val reason: String,
    ) : AutostartChange

    data class Rejected(
        val reason: String,
    ) : AutostartChange
}

interface AutostartUseCase {
    fun status(): AutostartStatus

    fun enable(mode: AutostartMode): AutostartChange

    fun disable(): AutostartChange
}
