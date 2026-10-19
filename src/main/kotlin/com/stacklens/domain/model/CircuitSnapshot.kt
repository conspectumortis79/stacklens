package com.stacklens.domain.model

data class CircuitSnapshot(
    val mcpConfigId: Long,
    val provider: String,
    val isOpen: Boolean,
    val failures: Int,
    val openedAt: Long,
    val probing: Boolean,
)
