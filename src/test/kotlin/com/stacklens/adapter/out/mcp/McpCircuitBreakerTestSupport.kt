package com.stacklens.adapter.out.mcp

internal fun McpCircuitBreaker.resetAll() = registry.clear()

internal fun McpCircuitBreaker.forceOpen(
    key: String,
    openedAt: Long = System.currentTimeMillis(),
) {
    registry[key] = McpCircuitBreaker.State(failures = 3, openedAt = openedAt, isOpen = true)
}
