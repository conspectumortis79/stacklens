package com.stacklens.application.collaboration

interface StartupRecovery {
    fun <T> heal(
        logTag: String,
        failedStatus: String = "FAILED",
        findStale: () -> List<T>,
        markFailed: (T) -> Unit,
        save: (T) -> Unit,
    )
}
