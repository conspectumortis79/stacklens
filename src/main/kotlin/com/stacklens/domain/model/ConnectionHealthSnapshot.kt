package com.stacklens.domain.model

import java.time.LocalDateTime

data class ConnectionHealthSnapshot(
    val id: Long,
    val kind: String,
    val name: String,
    val active: Boolean,
    val consecutiveFailures: Int,
    val lastErrorAt: LocalDateTime?,
    val lastErrorMessage: String?,
    val healthy: Boolean?,
)
