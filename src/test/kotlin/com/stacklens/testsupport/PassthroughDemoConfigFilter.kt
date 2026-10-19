package com.stacklens.testsupport

import com.stacklens.adapter.out.demo.DemoConfigFilter
import com.stacklens.application.port.out.UiPreferencesPort

object PassthroughDemoConfigFilter {
    val instance: DemoConfigFilter = DemoConfigFilter(DevDockerTestEnvOn)

    private object DevDockerTestEnvOn : UiPreferencesPort {
        override fun getAll(): Map<String, String> = mapOf("dev_docker_test_env" to "true")

        override fun replaceAll(preferences: Map<String, String>) {
        }
    }
}
