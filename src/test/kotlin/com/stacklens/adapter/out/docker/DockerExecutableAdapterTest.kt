package com.stacklens.adapter.out.docker

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DockerExecutableAdapterTest {
    private fun osPaths(macOS: Boolean): DockerOsPaths =
        object : DockerOsPaths({ if (macOS) "Mac OS X" else "Linux" }) {
            override fun isMacOS(): Boolean = macOS

            override fun searchDirs(): List<String> = listOf("/missing-stacklens-bin-${System.nanoTime()}")
        }

    @Test
    fun `no executable on macOS falls back to docker`() {
        val adapter = DockerExecutableAdapter(osPaths(macOS = true))

        assertTrue(adapter.isMacOS())
        assertEquals("docker", adapter.resolveDockerExecutable())
    }

    @Test
    fun `no executable on other platforms falls back to docker`() {
        val adapter = DockerExecutableAdapter(osPaths(macOS = false))

        assertFalse(adapter.isMacOS())
        assertEquals("docker", adapter.resolveDockerExecutable())
    }
}
