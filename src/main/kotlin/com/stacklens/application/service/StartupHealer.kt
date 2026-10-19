
package com.stacklens.application.service

import com.stacklens.application.collaboration.StartupRecovery
import com.stacklens.application.stereotype.ApplicationService
import org.slf4j.LoggerFactory

@ApplicationService
class StartupHealer : StartupRecovery {
    private val log = LoggerFactory.getLogger(StartupHealer::class.java)

    override fun <T> heal(
        logTag: String,
        failedStatus: String,
        findStale: () -> List<T>,
        markFailed: (T) -> Unit,
        save: (T) -> Unit,
    ) {
        runCatching {
            val stale = findStale()
            if (stale.isNotEmpty()) {
                log.warn(
                    "Startup-Healer: {} orphaned {}(s) will be set to {}.",
                    stale.size,
                    logTag,
                    failedStatus,
                )
                stale.forEach { entity ->
                    markFailed(entity)
                    runCatching { save(entity) }
                        .onFailure { e ->
                            log.error(
                                "Startup-Healer: error saving {}: {}",
                                logTag,
                                e.message,
                            )
                        }
                }
            }
        }.onFailure { e -> log.error("Startup-Healer {}: error: {}", logTag, e.message, e) }
    }
}
