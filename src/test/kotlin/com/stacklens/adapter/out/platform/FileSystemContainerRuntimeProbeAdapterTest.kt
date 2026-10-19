package com.stacklens.adapter.out.platform

import com.stacklens.application.port.out.platform.ContainerRuntime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class FileSystemContainerRuntimeProbeAdapterTest {
    @Test
    fun `detectActiveRuntime returns DOCKER when dockerenv file exists`(
        @TempDir tmp: Path,
    ) {
        Files.createFile(tmp.resolve("dockerenv"))
        val adapter =
            FileSystemContainerRuntimeProbeAdapter(
                dockerenvPath = tmp.resolve("dockerenv").toString(),
            )
        assertEquals(ContainerRuntime.DOCKER, adapter.detectActiveRuntime())
    }

    @Test
    fun `detectActiveRuntime returns DOCKER when DOCKER_RUNTIME env var contains docker`(
        @TempDir tmp: Path,
    ) {
        val adapter =
            FileSystemContainerRuntimeProbeAdapter(
                dockerenvPath = tmp.resolve("nope").toString(),
                dockerEnvVar = "/var/run/docker.sock",
            )
        assertEquals(ContainerRuntime.DOCKER, adapter.detectActiveRuntime())
    }

    @Test
    fun `detectActiveRuntime returns DOCKER even with uppercase DOCKER in env var`(
        @TempDir tmp: Path,
    ) {
        val adapter =
            FileSystemContainerRuntimeProbeAdapter(
                dockerenvPath = tmp.resolve("nope").toString(),
                dockerEnvVar = "DOCKER_HOST=tcp://example",
            )
        assertEquals(ContainerRuntime.DOCKER, adapter.detectActiveRuntime())
    }

    @Test
    fun `detectActiveRuntime returns PODMAN when PODMAN_ENVIRONMENT env var is non-blank`(
        @TempDir tmp: Path,
    ) {
        val adapter =
            FileSystemContainerRuntimeProbeAdapter(
                dockerenvPath = tmp.resolve("nope").toString(),
                dockerEnvVar = "",
                podmanEnvVar = "container",
                cgroupPath = tmp.resolve("nope").toString(),
            )
        assertEquals(ContainerRuntime.PODMAN, adapter.detectActiveRuntime())
    }

    @Test
    fun `detectActiveRuntime returns PODMAN when cgroup file contains libpod`(
        @TempDir tmp: Path,
    ) {
        val cgroup = Files.createFile(tmp.resolve("cgroup"))
        Files.writeString(cgroup, "12:devices:/libpod_parent")
        val adapter =
            FileSystemContainerRuntimeProbeAdapter(
                dockerenvPath = tmp.resolve("nope").toString(),
                dockerEnvVar = "",
                podmanEnvVar = "",
                cgroupPath = cgroup.toString(),
            )
        assertEquals(ContainerRuntime.PODMAN, adapter.detectActiveRuntime())
    }

    @Test
    fun `detectActiveRuntime returns SYSBOX when cgroup file contains sysbox`(
        @TempDir tmp: Path,
    ) {
        val cgroup = Files.createFile(tmp.resolve("cgroup"))
        Files.writeString(cgroup, "1:name=:/sysbox-fs")
        val adapter =
            FileSystemContainerRuntimeProbeAdapter(
                dockerenvPath = tmp.resolve("nope").toString(),
                dockerEnvVar = "",
                podmanEnvVar = "",
                cgroupPath = cgroup.toString(),
            )
        assertEquals(ContainerRuntime.SYSBOX, adapter.detectActiveRuntime())
    }

    @Test
    fun `detectActiveRuntime returns NONE when no marker is found`(
        @TempDir tmp: Path,
    ) {
        val cgroup = Files.createFile(tmp.resolve("cgroup"))
        Files.writeString(cgroup, "0::/user.slice/user-1000.slice/session-1.scope")
        val adapter =
            FileSystemContainerRuntimeProbeAdapter(
                dockerenvPath = tmp.resolve("nope").toString(),
                dockerEnvVar = "",
                podmanEnvVar = "",
                cgroupPath = cgroup.toString(),
            )
        assertEquals(ContainerRuntime.NONE, adapter.detectActiveRuntime())
    }

    @Test
    fun `detectActiveRuntime returns NONE when cgroup file does not exist`(
        @TempDir tmp: Path,
    ) {
        val adapter =
            FileSystemContainerRuntimeProbeAdapter(
                dockerenvPath = tmp.resolve("nope").toString(),
                dockerEnvVar = "",
                podmanEnvVar = "",
                cgroupPath = tmp.resolve("does-not-exist").toString(),
            )
        assertEquals(ContainerRuntime.NONE, adapter.detectActiveRuntime())
    }

    @Test
    fun `detectActiveRuntime swallows exceptions and returns NONE when reading cgroup throws`(
        @TempDir tmp: Path,
    ) {
        val cgroupDir = Files.createDirectory(tmp.resolve("cgroup-dir"))
        val adapter =
            FileSystemContainerRuntimeProbeAdapter(
                dockerenvPath = tmp.resolve("nope").toString(),
                dockerEnvVar = "",
                podmanEnvVar = "",
                cgroupPath = cgroupDir.toString(),
            )
        assertEquals(ContainerRuntime.NONE, adapter.detectActiveRuntime())
    }

    @Test
    fun `DOCKER wins over PODMAN when both markers are present`(
        @TempDir tmp: Path,
    ) {
        Files.createFile(tmp.resolve("dockerenv"))
        val cgroup = Files.createFile(tmp.resolve("cgroup"))
        Files.writeString(cgroup, "0::/libpod")
        val adapter =
            FileSystemContainerRuntimeProbeAdapter(
                dockerenvPath = tmp.resolve("dockerenv").toString(),
                dockerEnvVar = "",
                podmanEnvVar = "container",
                cgroupPath = cgroup.toString(),
            )
        assertEquals(ContainerRuntime.DOCKER, adapter.detectActiveRuntime())
    }

    @Test
    fun `detectActiveRuntime treats an unreadable cgroup file as NONE`(
        @TempDir tmp: Path,
    ) {
        val cgroup = Files.createFile(tmp.resolve("cgroup"))
        Files.writeString(cgroup, "0::/libpod_parent")
        cgroup.toFile().setReadable(false)
        try {
            val adapter =
                FileSystemContainerRuntimeProbeAdapter(
                    dockerenvPath = tmp.resolve("nope").toString(),
                    dockerEnvVar = "",
                    podmanEnvVar = "",
                    cgroupPath = cgroup.toString(),
                )
            assertEquals(ContainerRuntime.NONE, adapter.detectActiveRuntime())
        } finally {
            cgroup.toFile().setReadable(true)
        }
    }

    @Test
    fun `constructor uses System getenv when defaults are left unspecified`(
        @TempDir tmp: Path,
    ) {
        val adapter = FileSystemContainerRuntimeProbeAdapter()
        val runtime = adapter.detectActiveRuntime()
        assertNotNull(runtime.name)
    }

    @Test
    fun `readEnvVar returns empty string when env var is not set`() {
        val result = FileSystemContainerRuntimeProbeAdapter.readEnvVarOrEmpty("STACKLENS_DEFINITELY_UNSET_${System.nanoTime()}")
        assertEquals("", result)
    }

    @Test
    fun `readEnvVar returns the env var value when it is set`() {
        val result = FileSystemContainerRuntimeProbeAdapter.readEnvVarOrEmpty("USER")
        assert(result.isNotEmpty()) { "USER env var should be set in the test environment" }
    }

    @Test
    fun `secondary constructor injects an env-var reader`(
        @TempDir tmp: Path,
    ) {
        val adapter =
            FileSystemContainerRuntimeProbeAdapter(
                dockerenvPath = tmp.resolve("nope").toString(),
                cgroupPath = tmp.resolve("nope").toString(),
                envVarReader = { name ->
                    when (name) {
                        "DOCKER_RUNTIME" -> "/var/run/docker.sock"
                        else -> ""
                    }
                },
            )
        assertEquals(ContainerRuntime.DOCKER, adapter.detectActiveRuntime())
    }
}
