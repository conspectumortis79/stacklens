package com.stacklens.adapter.inbound.rest.config

import com.stacklens.application.port.out.UiPreferencesPort
import com.stacklens.application.port.out.textfilter.TextFilterConfigPort

internal class StubUiPreferences(
    values: Map<String, String> = emptyMap(),
) : UiPreferencesPort {
    var stored: MutableMap<String, String> = values.toMutableMap()

    override fun getAll(): Map<String, String> = stored.toMap()

    override fun replaceAll(preferences: Map<String, String>) {
        stored = preferences.toMutableMap()
    }
}

internal class StubTextFilter(
    initialEnabled: Boolean = true,
) : TextFilterConfigPort {
    override var enabled: Boolean = initialEnabled
        private set

    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }
}
