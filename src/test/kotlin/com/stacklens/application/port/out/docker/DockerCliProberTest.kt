package com.stacklens.application.port.out.docker

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DockerCliProberTest {
    @Test
    fun `the port can be implemented as a lambda`() {
        val status =
            DockerCliProber {
                com.stacklens.domain.docker
                    .DockerStatus(true, "24.0.5")
            }.probe()
        assertTrue(status.available)
    }
}
