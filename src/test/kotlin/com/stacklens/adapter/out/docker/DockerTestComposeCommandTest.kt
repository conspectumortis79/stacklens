package com.stacklens.adapter.out.docker

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DockerTestComposeCommandTest {
    private val composeFile: String = "/tmp/stacklens-project/docker/docker-compose.yml"

    @Test
    fun `compose args put the project flag behind the compose subcommand`() {
        assertEquals(
            listOf("compose", "-p", TESTDOCKER_PROJECT_NAME, "-f", composeFile, "build"),
            testdockerComposeArgs(composeFile, "build"),
        )
    }

    @Test
    fun `compose command prefixes the resolved docker executable`() {
        assertEquals(
            listOf("/usr/local/bin/docker", "compose", "-p", TESTDOCKER_PROJECT_NAME, "-f", composeFile, "down", "--volumes"),
            testdockerComposeCommand("/usr/local/bin/docker", composeFile, "down", "--volumes"),
        )
    }

    @Test
    fun `compose command keeps the subcommand before any file flag`() {
        val command = testdockerComposeCommand("docker", composeFile, "up", "-d")
        assertEquals("compose", command[1])
        assertEquals(command.indexOf("compose") + 1, command.indexOf("-p"))
        assertEquals(command.indexOf("-p") + 2, command.indexOf("-f"))
    }
}
