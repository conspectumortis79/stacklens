package com.stacklens.application.service

import com.stacklens.application.port.out.UiPreferencesPort
import com.stacklens.application.port.out.appconfig.StackLensConfigPort
import com.stacklens.application.port.out.async.StartupTaskExecutor
import com.stacklens.application.port.out.docker.DemoEnvironmentOutcome
import com.stacklens.application.port.out.docker.DemoEnvironmentPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class DockerEnvironmentStartupServiceTest {
    private class RecordingDemoEnvironment : DemoEnvironmentPort {
        val timeouts = mutableListOf<Long>()

        override fun bringUp(timeoutSeconds: Long): DemoEnvironmentOutcome? {
            timeouts += timeoutSeconds
            return DemoEnvironmentOutcome(0, "network created")
        }

        override fun bringDown() = Unit

        override fun ensureMysqlSchema() = Unit
    }

    private val demoEnvironment = RecordingDemoEnvironment()

    private val configPort: StackLensConfigPort =
        mock {
            on { dockerComposeUpTimeoutSeconds } doReturn 120L
        }

    private fun service(
        prefs: Map<String, String>,
        runner: StartupTaskExecutor = ImmediateStartupTask,
    ): DockerEnvironmentStartupService =
        DockerEnvironmentStartupService(
            uiPreferences =
                object : UiPreferencesPort {
                    override fun getAll(): Map<String, String> = prefs

                    override fun replaceAll(preferences: Map<String, String>) = Unit
                },
            demoEnvironment = demoEnvironment,
            configPort = configPort,
            startupTasks = runner,
        )

    @Test
    fun `nothing happens while both developer toggles are off`() {
        service(mapOf("theme" to "dark")).startDemoEnvironmentIfEnabledInBackground()

        assertEquals(emptyList<Long>(), demoEnvironment.timeouts)
    }

    @Test
    fun `an explicit false flag keeps the environment down`() {
        service(mapOf("dev_docker_auto_start" to "false", "dev_docker_test_env" to "false")).startDemoEnvironmentIfEnabledInBackground()

        assertEquals(emptyList<Long>(), demoEnvironment.timeouts)
    }

    @Test
    fun `the auto start flag brings the demo profile up`() {
        service(mapOf("dev_docker_auto_start" to "true")).startDemoEnvironmentIfEnabledInBackground()

        assertEquals(listOf(120L), demoEnvironment.timeouts)
    }

    @Test
    fun `the test environment flag alone brings the demo profile up`() {
        service(mapOf("dev_docker_test_env" to "true")).startDemoEnvironmentIfEnabledInBackground()

        assertEquals(listOf(120L), demoEnvironment.timeouts)
    }

    @Test
    fun `the configured timeout budget is handed to the compose run`() {
        org.mockito.kotlin
            .whenever(configPort.dockerComposeUpTimeoutSeconds)
            .thenReturn(7L)

        service(mapOf("dev_docker_auto_start" to "true")).startDemoEnvironmentIfEnabledInBackground()

        assertEquals(listOf(7L), demoEnvironment.timeouts)
    }

    @Test
    fun `the startup work is handed to the background executor`() {
        val recorded = mutableListOf<() -> Unit>()
        val collecting =
            object : StartupTaskExecutor {
                override fun submit(task: () -> Unit) {
                    recorded += task
                }
            }

        service(mapOf("dev_docker_auto_start" to "true"), runner = collecting).startDemoEnvironmentIfEnabledInBackground()

        assertEquals(1, recorded.size)
        assertEquals(emptyList<Long>(), demoEnvironment.timeouts)
        recorded.single().invoke()
        assertEquals(listOf(120L), demoEnvironment.timeouts)
    }

    private object ImmediateStartupTask : StartupTaskExecutor {
        override fun submit(task: () -> Unit) {
            task()
        }
    }
}
