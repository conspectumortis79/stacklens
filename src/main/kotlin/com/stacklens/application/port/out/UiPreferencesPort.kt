package com.stacklens.application.port.out

interface UiPreferencesPort {
    fun getAll(): Map<String, String>

    fun replaceAll(preferences: Map<String, String>)
}
