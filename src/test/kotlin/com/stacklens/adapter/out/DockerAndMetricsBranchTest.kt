package com.stacklens.adapter.out

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.out.docker.ComposeContainerStateAdapter
import com.stacklens.adapter.out.docker.DockerCommandAdapter
import com.stacklens.adapter.out.docker.DockerExecutableAdapter
import com.stacklens.adapter.out.docker.DockerOsPaths
import com.stacklens.adapter.out.docker.DockerProcessEnvironment
import com.stacklens.adapter.out.docker.DockerSocketDiscoveryAdapter
import com.stacklens.adapter.out.grafana.DashboardMetricsParser
import com.stacklens.adapter.out.platform.HostBrowserAdapter
import com.stacklens.application.port.out.platform.OsType
import com.stacklens.testsupport.FakePlatformPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class DockerAndMetricsBranchTest {
    @Test
    fun `a compose command with a work dir is started inside that directory`() {
        val adapter = RecordingDockerAdapter()

        val outcome = adapter.runDockerCommandWithTreeKill(listOf("/bin/echo", "in-dir"), "/tmp", 10L)

        assertEquals(0, outcome.exitCode)
        assertEquals(File("/tmp"), adapter.seenDir)
    }

    @Test
    fun `a compose ps row with a non numeric exit column falls back to zero`() {
        val state =
            ComposeContainerStateAdapter(
                DockerExecutableAdapter(DockerOsPaths()),
                DockerProcessEnvironment(DockerOsPaths(), DockerSocketDiscoveryAdapter()),
            ).parseComposePs("n\tup\tnot-a-number\n")

        assertEquals(0, state.entries.single().exitCode)
    }

    @Test
    fun `a sub panel without any metric target contributes nothing`() {
        val json =
            """
            {"dashboard":{"panels":[{"id":1,"title":"row","type":"row","panels":[
            {"id":2,"title":"empty","type":"timeseries","targets":[{"refId":"A"}]}]}]}}
            """.trimIndent()

        assertEquals(emptyList<Map<String, Any?>>(), DashboardMetricsParser(ObjectMapper()).extractMetricsList(json))
    }

    @Test
    fun `the browser adapter really starts the platform command and falls back to the desktop`() {
        val browsed = mutableListOf<java.net.URI>()
        val desktop = org.mockito.Mockito.mock(java.awt.Desktop::class.java)
        org.mockito.Mockito
            .`when`(desktop.browse(org.mockito.kotlin.any<java.net.URI>()))
            .thenAnswer { invocation -> browsed.add(invocation.getArgument(0)) }
        val adapter = HostBrowserAdapter(FakePlatformPort(OsType.WINDOWS), desktopSupplier = { desktop })

        adapter.browse("https://example.com")

        assertEquals(listOf(java.net.URI("https://example.com")), browsed)
    }

    private class RecordingDockerAdapter : DockerCommandAdapter(DockerProcessEnvironment(DockerOsPaths(), DockerSocketDiscoveryAdapter())) {
        var seenDir: File? = null

        override fun startComposeProcess(
            command: List<String>,
            workDir: String?,
        ): Process {
            seenDir = workDir?.let(::File)
            return ProcessBuilder(command).redirectErrorStream(true).start()
        }
    }
}
