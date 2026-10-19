package com.stacklens.adapter.out

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.out.docker.DockerCommandAdapter
import com.stacklens.adapter.out.docker.DockerOsPaths
import com.stacklens.adapter.out.docker.DockerProcessEnvironment
import com.stacklens.adapter.out.grafana.DashboardMetricsParser
import com.stacklens.application.port.out.docker.DockerAvailabilityPort
import com.stacklens.domain.docker.DockerStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class DockerComposeAdapterBranchTest {
    private class RecordingAdapter :
        DockerCommandAdapter(
            DockerProcessEnvironment(
                DockerOsPaths(),
                object : com.stacklens.application.port.out.docker.DockerSocketDiscoveryPort {
                    override fun findAllDockerSockets(): List<String> = emptyList()
                },
            ),
        ) {
        var startedWith: List<String> = emptyList()
        var capturedDir: File? = null
        var commandToRun: List<String> = listOf("/bin/echo", "hello")

        override fun startComposeProcess(
            command: List<String>,
            workDir: String?,
        ): Process {
            startedWith = command
            capturedDir = workDir?.let(::File)
            return ProcessBuilder(commandToRun).redirectErrorStream(true).start()
        }
    }

    @Test
    fun `a compose command that runs to completion reports its exit code and trimmed output`() {
        val adapter = RecordingAdapter()

        val outcome = adapter.runDockerCommandWithTreeKill(listOf("docker", "ps"), "/tmp", 10L)

        assertEquals(0, outcome.exitCode)
        assertEquals("hello", outcome.output)
        assertEquals(listOf("docker", "ps"), adapter.startedWith)
        assertEquals(File("/tmp"), adapter.capturedDir)
    }

    @Test
    fun `a compose command that never finishes is killed and reported as a timeout`() {
        val adapter = RecordingAdapter()
        adapter.commandToRun = listOf("/bin/sleep", "30")

        val outcome = adapter.runDockerCommandWithTreeKill(listOf("docker", "slow"), null, 1L)

        assertEquals(-1, outcome.exitCode)
        assertEquals("timeout after 1s", outcome.output)
        assertEquals(null, adapter.capturedDir)
    }
}

class DashboardMetricsParserBranchTest {
    private val parser = DashboardMetricsParser(ObjectMapper())

    @Test
    fun `metrics of collapsed sub panels are collected too`() {
        val json =
            """
            {
              "dashboard": {
                "panels": [
                  {
                    "id": 1, "title": "row", "type": "row",
                    "panels": [
                      {
                        "id": 2, "title": "cpu", "type": "timeseries",
                        "targets": [ { "refId": "A", "expr": "rate(x[5m])", "legendFormat": "__auto" } ]
                      }
                    ]
                  }
                ]
              }
            }
            """.trimIndent()

        val metrics = parser.extractMetricsList(json)

        assertEquals(1, metrics.size)
        assertEquals("rate(x[5m])", metrics.single()["metric"])
        assertEquals("cpu", metrics.single()["panelTitle"])
    }
}

class DockerAvailabilityDefaultTest {
    @Test
    fun `the default availability check reads the status of the probe`() {
        val useCase =
            object : DockerAvailabilityPort {
                override fun checkDockerAvailability(): DockerStatus = DockerStatus(true, "socket ping ok")
            }

        assertTrue(useCase.isDockerAvailable())
    }
}
