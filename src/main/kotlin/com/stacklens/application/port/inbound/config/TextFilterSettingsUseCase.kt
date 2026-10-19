package com.stacklens.application.port.inbound.config

interface TextFilterSettingsUseCase {
    fun isTextFilterEnabled(): Boolean

    fun setTextFilterEnabled(enabled: Boolean): Boolean
}
