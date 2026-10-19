package com.stacklens.application.port.out.mcp

import com.stacklens.domain.model.CircuitSnapshot

fun interface McpCircuitStatePort {
    fun snapshot(): Map<String, CircuitSnapshot>
}
