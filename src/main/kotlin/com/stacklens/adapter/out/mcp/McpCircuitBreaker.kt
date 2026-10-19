package com.stacklens.adapter.out.mcp

import com.stacklens.domain.model.CircuitSnapshot
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class McpCircuitBreaker {
    private val log = LoggerFactory.getLogger(McpCircuitBreaker::class.java)

    private companion object {
        const val FAILURE_THRESHOLD = 3

        const val COOLDOWN_MS = 60_000L
    }

    internal data class State(
        val failures: Int = 0,
        val openedAt: Long = 0L,
        val isOpen: Boolean = false,
        val probing: Boolean = false,
    )

    internal val registry = ConcurrentHashMap<String, State>()

    fun isAllowed(key: String): Boolean {
        var allowed = false
        var probeStarted = false
        registry.compute(key) { _, current ->
            val state = current
            when {
                state == null -> {
                    allowed = true
                    null
                }
                !state.isOpen -> {
                    allowed = true
                    state
                }
                System.currentTimeMillis() - state.openedAt < COOLDOWN_MS -> state
                state.probing -> state
                else -> {
                    allowed = true
                    probeStarted = true
                    state.copy(probing = true)
                }
            }
        }
        if (probeStarted) log.info("[MCPCircuit:{}] half-open – sending one probe call", key)
        return allowed
    }

    fun recordSuccess(key: String) {
        if (registry.remove(key) != null) {
            log.info("[MCPCircuit:{}] circuit CLOSED – MCP routing restored", key)
        }
    }

    fun recordFailure(key: String) {
        registry.compute(key) { _, current ->
            val wasOpen = current?.isOpen ?: false
            val failures = (current?.failures ?: 0) + 1
            val nowOpen = wasOpen || failures >= FAILURE_THRESHOLD
            if (nowOpen && !wasOpen) {
                log.warn(
                    "[MCPCircuit:{}] circuit OPENED after {} consecutive failures – native fallback active for {}s",
                    key,
                    failures,
                    COOLDOWN_MS / 1000,
                )
                State(failures = failures, openedAt = System.currentTimeMillis(), isOpen = true)
            } else {
                State(
                    failures = failures,
                    openedAt =
                        if (wasOpen) {
                            if (current.probing) System.currentTimeMillis() else current.openedAt
                        } else {
                            0L
                        },
                    isOpen = nowOpen,
                )
            }
        }
    }

    fun providerOf(tool: String): String = tool.substringBefore('_')

    fun keyFor(
        mcpConfigId: Long,
        tool: String,
    ): String = "$mcpConfigId:${providerOf(tool)}"

    fun snapshot(): Map<String, CircuitSnapshot> =
        registry.entries.associate { (key, state) ->
            val parts = key.split(':', limit = 2)
            val mcpConfigId = parts.getOrNull(0)?.toLongOrNull() ?: 0L
            val provider = parts.getOrNull(1) ?: ""
            key to
                CircuitSnapshot(
                    mcpConfigId = mcpConfigId,
                    provider = provider,
                    isOpen = state.isOpen,
                    failures = state.failures,
                    openedAt = state.openedAt,
                    probing = state.probing,
                )
        }
}
