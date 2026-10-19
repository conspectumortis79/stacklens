package com.stacklens.adapter.out.demo

import com.stacklens.application.port.out.UiPreferencesPort
import com.stacklens.application.port.out.platform.DemoConfigPort
import org.springframework.stereotype.Component

@Component
class DemoConfigFilter(
    private val uiPreferences: UiPreferencesPort,
) : DemoConfigPort {
    override fun <T> apply(
        configs: List<T>,
        nameOf: (T) -> String,
    ): List<T> {
        val testEnvOn = runCatching { uiPreferences.getAll()["dev_docker_test_env"] }.getOrNull() == "true"
        if (testEnvOn) return configs
        return configs.filterNot { nameOf(it).trim().startsWith("Demo ") }
    }
}
