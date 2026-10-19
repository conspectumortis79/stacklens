package com.stacklens.application.port.inbound.config

interface UiPreferencesUseCase {
    fun allPreferences(): Map<String, String>

    fun preference(key: String): String

    fun setPreference(
        key: String,
        value: String,
    )
}
