package com.stacklens.application.port.out.platform

import com.stacklens.domain.autostart.AutostartMode

interface AutostartStore {
    fun isInstalled(): Boolean

    fun installedMode(): AutostartMode

    fun install(mode: AutostartMode)

    fun uninstall()
}

class AutostartStorageException(
    val i18nKey: String,
    val path: String,
    cause: Throwable? = null,
) : RuntimeException("$i18nKey:$path", cause)
