package com.stacklens.application.service.config

import com.stacklens.application.collaboration.DevEnvironmentToggle
import com.stacklens.application.port.inbound.config.UiPreferencesUseCase
import com.stacklens.application.port.out.UiPreferencesPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.application.stereotype.ManuallyWired

@ApplicationService
@ManuallyWired
class UiPreferencesService(
    private val preferences: UiPreferencesPort,
    private val devEnvironmentToggle: DevEnvironmentToggle,
) : UiPreferencesUseCase {
    override fun allPreferences(): Map<String, String> = preferences.getAll()

    override fun preference(key: String): String = preferences.getAll()[key] ?: ""

    override fun setPreference(
        key: String,
        value: String,
    ) {
        val updated = preferences.getAll().toMutableMap()
        updated[key] = value
        preferences.replaceAll(updated)
        if (key == UiPreferenceVocabulary.DOCKER_TEST_ENV_KEY) {
            devEnvironmentToggle.reconcileDockerTestEnv(value == TRUE_VALUE)
        }
    }

    private companion object {
        const val TRUE_VALUE: String = "true"
    }
}
