package com.stacklens.application.service

import com.stacklens.application.port.inbound.platform.DockerEnvironmentStartupUseCase
import com.stacklens.application.port.out.UiPreferencesPort
import com.stacklens.application.port.out.appconfig.StackLensConfigPort
import com.stacklens.application.port.out.async.StartupTaskExecutor
import com.stacklens.application.port.out.docker.DemoEnvironmentPort
import com.stacklens.application.service.config.UiPreferenceVocabulary
import com.stacklens.application.stereotype.ApplicationService
import org.slf4j.LoggerFactory

@ApplicationService
class DockerEnvironmentStartupService(
    private val uiPreferences: UiPreferencesPort,
    private val demoEnvironment: DemoEnvironmentPort,
    private val configPort: StackLensConfigPort,
    private val startupTasks: StartupTaskExecutor,
) : DockerEnvironmentStartupUseCase {
    private val log = LoggerFactory.getLogger(DockerEnvironmentStartupService::class.java)

    override fun startDemoEnvironmentIfEnabledInBackground() {
        val prefs = uiPreferences.getAll()
        val autoStart = UiPreferenceVocabulary.booleanPreference(prefs, UiPreferenceVocabulary.DOCKER_AUTO_START_KEY, false)
        val testEnv = UiPreferenceVocabulary.booleanPreference(prefs, UiPreferenceVocabulary.DOCKER_TEST_ENV_KEY, false)
        if (!autoStart && !testEnv) return
        startupTasks.submit { startDemoEnvironment(autoStart, testEnv) }
    }

    private fun startDemoEnvironment(
        autoStart: Boolean,
        testEnv: Boolean,
    ) {
        log.info(
            "[DockerAutoStart] auto-start enabled (autoStart={}, testEnv={}) – starting Docker test environment in background",
            autoStart,
            testEnv,
        )
        try {
            val outcome = demoEnvironment.bringUp(configPort.dockerComposeUpTimeoutSeconds)
            if (outcome == null) {
                log.warn("[DockerAutoStart] no docker-compose.yml found, skipping")
                return
            }
            log.info("[DockerAutoStart] docker compose up -d exited with code {}", outcome.exitCode)
            if (outcome.exitCode != 0 && outcome.output.isNotBlank()) {
                log.warn(
                    "[DockerAutoStart] demo environment incomplete, compose output: {}",
                    outcome.output
                        .lineSequence()
                        .filter { it.isNotBlank() }
                        .joinToString("\n")
                        .take(MAX_FAILED_OUTPUT_CHARS),
                )
            }
        } catch (e: Exception) {
            log.warn("[DockerAutoStart] failed: {}", e.message)
        }
    }

    private companion object {
        const val MAX_FAILED_OUTPUT_CHARS = 4_000
    }
}
