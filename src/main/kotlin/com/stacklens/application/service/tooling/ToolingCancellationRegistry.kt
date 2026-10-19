package com.stacklens.application.service.tooling

import com.stacklens.application.stereotype.ApplicationService
import java.util.concurrent.ConcurrentHashMap

@ApplicationService
class ToolingCancellationRegistry {
    private val cancelledToolIds: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    fun markCancelled(toolId: Long) {
        cancelledToolIds.add(toolId)
    }

    fun consumeCancellation(toolId: Long): Boolean = cancelledToolIds.remove(toolId)
}
