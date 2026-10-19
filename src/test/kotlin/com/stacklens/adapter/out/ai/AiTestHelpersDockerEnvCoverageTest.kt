package com.stacklens.adapter.out.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class AiTestHelpersDockerEnvCoverageTest {
    @Test
    fun `a missing docker env marker keeps the loopback url`(
        @TempDir dir: Path,
    ) {
        val missing = dir.resolve("dockerenv").toString()

        assertEquals(
            "http://localhost:1234/v1/models",
            AiTestHelpers.resolveDockerUrl("http://localhost:1234/v1/models", null, missing),
        )
    }

    @Test
    fun `an existing docker env marker sends the url through the container host`(
        @TempDir dir: Path,
    ) {
        val marker = dir.resolve(".dockerenv").toFile()
        marker.createNewFile()

        assertEquals(
            "http://host.docker.internal:1234/v1/models",
            AiTestHelpers.resolveDockerUrl("http://localhost:1234/v1/models", null, marker.absolutePath),
        )
    }
}
