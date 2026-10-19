package com.stacklens.adapter.out.mcp

import com.stacklens.application.port.out.mcp.McpCircuitStatePort
import com.stacklens.domain.model.CircuitSnapshot
import org.springframework.stereotype.Component

@Component
class McpCircuitStateAdapter(
    private val circuitBreaker: McpCircuitBreaker,
) : McpCircuitStatePort {
    override fun snapshot(): Map<String, CircuitSnapshot> = circuitBreaker.snapshot()
}
