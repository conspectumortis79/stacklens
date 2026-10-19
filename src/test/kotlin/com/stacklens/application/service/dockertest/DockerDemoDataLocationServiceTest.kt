package com.stacklens.application.service.dockertest

import com.stacklens.application.port.out.docker.ComposeFileLocationPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DockerDemoDataLocationServiceTest {
    private val composeFiles = mock<ComposeFileLocationPort>()

    @Test
    fun `the location is forwarded from the compose file port`() {
        whenever(composeFiles.resolveComposeFile()).thenReturn("/opt/stacklens/docker-compose.yml")

        assertEquals("/opt/stacklens/docker-compose.yml", DockerDemoDataLocationService(composeFiles).composeFileLocation())
    }

    @Test
    fun `a missing compose file is reported as no location`() {
        whenever(composeFiles.resolveComposeFile()).thenReturn(null)

        assertEquals(null, DockerDemoDataLocationService(composeFiles).composeFileLocation())
    }
}
