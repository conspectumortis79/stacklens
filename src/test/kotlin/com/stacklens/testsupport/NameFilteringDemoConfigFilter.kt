package com.stacklens.testsupport

import com.stacklens.adapter.out.demo.DemoConfigFilter
import com.stacklens.application.port.out.UiPreferencesPort
import com.stacklens.application.port.out.platform.DemoConfigPort

object NameFilteringDemoConfigFilter {
    val instance: DemoConfigPort = DemoConfigFilter(DevDockerTestEnvOff)

    private object DevDockerTestEnvOff : UiPreferencesPort {
        override fun getAll(): Map<String, String> = emptyMap()

        override fun replaceAll(preferences: Map<String, String>) = Unit
    }
}
