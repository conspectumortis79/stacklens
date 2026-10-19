package com.stacklens.application.service

import com.stacklens.application.stereotype.ApplicationService
import java.util.concurrent.ConcurrentHashMap

@ApplicationService
class SearchCancellationRegistry {
    private val cancelledSessionIds: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    fun cancel(sessionId: Long) {
        cancelledSessionIds.add(sessionId)
    }

    fun remove(sessionId: Long) {
        cancelledSessionIds.remove(sessionId)
    }

    fun isCancelled(sessionId: Long): Boolean = cancelledSessionIds.contains(sessionId)
}
