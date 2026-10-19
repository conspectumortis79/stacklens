package com.stacklens.adapter.out.docker

import com.stacklens.adapter.out.docker.TESTDOCKER_PROJECT_NAME
import com.stacklens.domain.docker.DockerCommandOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DockerComposeAdapterQueryTest {
    private fun adapter(dockerExecutable: String) =
        ComposeContainerStateAdapter(
            object : com.stacklens.application.port.out.docker.DockerExecutablePort {
                override fun resolveDockerExecutable(): String = dockerExecutable
            },
            object : DockerProcessEnvironment(
                DockerOsPaths(),
                object : com.stacklens.application.port.out.docker.DockerSocketDiscoveryPort {
                    override fun findAllDockerSockets(): List<String> = emptyList()
                },
            ) {
                override fun apply(pb: ProcessBuilder) {}
            },
        )

    private fun processEnvironment(): DockerProcessEnvironment =
        DockerProcessEnvironment(
            DockerOsPaths(),
            object : com.stacklens.application.port.out.docker.DockerSocketDiscoveryPort {
                override fun findAllDockerSockets(): List<String> = emptyList()
            },
        )

    private fun emptySockets(): com.stacklens.application.port.out.docker.DockerSocketDiscoveryPort =
        object : com.stacklens.application.port.out.docker.DockerSocketDiscoveryPort {
            override fun findAllDockerSockets(): List<String> = emptyList()
        }

    private fun commandAdapter(): DockerCommandAdapter = DockerCommandAdapter(processEnvironment())

    private fun stubDocker(
        dir: File,
        script: String,
        name: String = "docker",
    ): File =
        File(dir, name).apply {
            writeText(script)
            setExecutable(true, false)
        }

    private fun composeFileIn(dir: File): File = File(dir, "docker-compose.yml").apply { writeText("services: {}") }

    @Test
    fun `the compose ps output is parsed into container states`(
        @TempDir dir: File,
    ) {
        val docker =
            stubDocker(
                dir,
                """
                #!/bin/sh
                printf 'web\trunning\t0\nworker\texited\t1\nsidecar\texited\t0\nbroken-line\n'
                """.trimIndent(),
            )

        val state = adapter(docker.absolutePath).queryComposeContainers(composeFileIn(dir).absolutePath, timeoutSeconds = 20L)

        assertEquals(3, state.total)
        assertEquals(1, state.running)
        assertTrue(state.anyExitedNonZero)
        assertEquals("web", state.entries.first().name)
        assertEquals(1, state.entries[1].exitCode)
    }

    @Test
    fun `a compose command that outputs nothing yields no containers`(
        @TempDir dir: File,
    ) {
        val docker = stubDocker(dir, "#!/bin/sh\ntrue\n")

        assertEquals(0, adapter(docker.absolutePath).queryComposeContainers(composeFileIn(dir).absolutePath, timeoutSeconds = 20L).total)
    }

    @Test
    fun `a docker process that outlives the budget is destroyed and yields an empty state`(
        @TempDir dir: File,
    ) {
        val hanging = stubDocker(dir, "#!/bin/sh\nexec 1>&- 2>&-\nexec sleep 30\n", name = "hanging-docker")

        val state = adapter(hanging.absolutePath).queryComposeContainers(composeFileIn(dir).absolutePath, timeoutSeconds = 1L)

        assertEquals(0, state.total)
    }

    @Test
    fun `a missing docker binary yields an empty state`(
        @TempDir dir: File,
    ) {
        val state = adapter(File(dir, "not-built").absolutePath).queryComposeContainers(composeFileIn(dir).absolutePath, timeoutSeconds = 5L)

        assertEquals(0, state.total)
    }

    @Test
    fun `a broken output stream is swallowed and the exit code still arrives`(
        @TempDir dir: File,
    ) {
        val broken =
            object : java.io.InputStream() {
                override fun read(): Int = throw java.io.IOException("broken pipe")

                override fun read(
                    b: ByteArray,
                    off: Int,
                    len: Int,
                ): Int = throw java.io.IOException("broken pipe")
            }
        val process = org.mockito.kotlin.mock<Process>()
        org.mockito.kotlin
            .whenever(process.inputStream)
            .thenReturn(broken)
        org.mockito.kotlin
            .whenever(process.waitFor())
            .thenReturn(9)

        val exit =
            object : DockerCommandAdapter(processEnvironment()) {
                override fun startComposeProcess(
                    command: List<String>,
                    workDir: String?,
                ): Process = process
            }.runComposeStreaming(listOf("true"), dir.absolutePath) { }

        org.junit.jupiter.api.Assertions
            .assertEquals(9, exit)
    }

    @Test
    fun `runDockerCommand merges stderr into the captured output`(
        @TempDir dir: File,
    ) {
        val script =
            stubDocker(
                dir,
                """
                #!/bin/sh
                echo stdout-zeile
                echo stderr-zeile >&2
                """.trimIndent(),
            )

        val outcome =
            commandAdapter().runDockerCommand(
                command = listOf(script.absolutePath),
                workDir = dir.absolutePath,
                timeoutSeconds = 20L,
            )

        assertEquals(0, outcome.exitCode)
        assertTrue(outcome.output.contains("stdout-zeile"))
        assertTrue(outcome.output.contains("stderr-zeile"))
    }

    @Test
    fun `runDockerCommand reports a timeout as exit -1 and destroys the process`(
        @TempDir dir: File,
    ) {
        val hanging = stubDocker(dir, "#!/bin/sh\nexec 1>&- 2>&-\nexec sleep 30\n", name = "hanging")

        val outcome =
            commandAdapter().runDockerCommand(
                command = listOf(hanging.absolutePath),
                workDir = dir.absolutePath,
                timeoutSeconds = 1L,
            )

        assertEquals(DockerCommandOutcome(-1, ""), outcome.copy(output = ""))
    }

    @Test
    fun `runDockerCommand applies the docker environment before starting`(
        @TempDir dir: File,
    ) {
        val printer =
            DockerCommandAdapter(
                object : DockerProcessEnvironment(DockerOsPaths(), emptySockets()) {
                    override fun apply(pb: ProcessBuilder) {
                        pb.environment()["PATH"] = "/usr/local/bin:" + (pb.environment()["PATH"] ?: "")
                    }
                },
            )
        val script = stubDocker(dir, "#!/bin/sh\nprintf '%s' \"\$PATH\"\n", name = "path-printer")

        val outcome =
            printer.runDockerCommand(
                command = listOf(script.absolutePath),
                workDir = dir.absolutePath,
                timeoutSeconds = 20L,
            )

        assertTrue(outcome.output.contains("/usr/local/bin"))
    }

    @Test
    fun `runComposeStreaming applies the docker environment and streams each line`(
        @TempDir dir: File,
    ) {
        val script =
            stubDocker(
                dir,
                """
                #!/bin/sh
                echo erste-zeile
                echo zweite-zeile
                """.trimIndent(),
            )
        val seen = mutableListOf<String>()

        val exit =
            DockerCommandAdapter(
                object : DockerProcessEnvironment(DockerOsPaths(), emptySockets()) {
                    override fun apply(pb: ProcessBuilder) {
                        pb.environment()["PATH"] = "/usr/local/bin:" + (pb.environment()["PATH"] ?: "")
                    }
                },
            ).runComposeStreaming(listOf(script.absolutePath), dir.absolutePath) { seen += it }

        assertEquals(0, exit)
        assertEquals(listOf("erste-zeile", "zweite-zeile"), seen)
    }

    @Test
    fun `the ps command queries the same compose project the environment starts in`(
        @TempDir dir: File,
    ) {
        val composeFile = composeFileIn(dir)

        val command = adapter("docker").composePsCommand(composeFile.absolutePath)

        assertEquals(
            listOf(
                "docker",
                "compose",
                "-p",
                TESTDOCKER_PROJECT_NAME,
                "-f",
                composeFile.absolutePath,
                "ps",
                "-a",
                "--format",
                "{{.Name}}\t{{.State}}\t{{.ExitCode}}",
            ),
            command,
        )
    }
}
