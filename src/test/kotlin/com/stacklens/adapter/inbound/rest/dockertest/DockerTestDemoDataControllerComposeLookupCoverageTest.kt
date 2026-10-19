package com.stacklens.adapter.inbound.rest.dockertest

import com.stacklens.application.port.inbound.dockertest.DockerDemoDataFileUseCase
import com.stacklens.application.port.inbound.dockertest.DockerDemoDataLocationUseCase
import com.stacklens.application.port.inbound.dockertest.DockerDemoDataReadResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus

class DockerTestDemoDataControllerComposeLookupCoverageTest {
    private val composeEnvironment: DockerDemoDataLocationUseCase = mock()
    private val demoDataFiles: DockerDemoDataFileUseCase = mock()

    @Test
    fun `a compose lookup that throws leaves the file list empty`() {
        whenever(composeEnvironment.composeFileLocation()).thenThrow(IllegalStateException("daemon gone"))
        whenever(demoDataFiles.listDataFiles(null)).thenReturn(emptyList())
        val controller =
            DockerTestDemoDataController(
                demoDataFiles,
                com.stacklens.application.service
                    .FakeI18n(),
                demoDataLocation = composeEnvironment,
            )

        val response = controller.listDataFiles()

        assertEquals(HttpStatus.OK.value(), response.statusCode.value())
        assertEquals(emptyList<Map<String, String>>(), response.body)
    }

    @Test
    fun `a controller without a compose environment lists no files`() {
        whenever(demoDataFiles.listDataFiles(null)).thenReturn(emptyList())
        val controller =
            DockerTestDemoDataController(
                demoDataFiles,
                com.stacklens.application.service
                    .FakeI18n(),
                demoDataLocation = mock(),
            )

        assertEquals(emptyList<Map<String, String>>(), controller.listDataFiles().body)
    }

    @Test
    fun `the resolved compose file below the workspace is forwarded to the use case`() {
        val composeFile = "/srv/project/docker-compose.yml"
        whenever(composeEnvironment.composeFileLocation()).thenReturn(composeFile)
        whenever(demoDataFiles.listDataFiles(composeFile)).thenReturn(emptyList())
        val controller =
            DockerTestDemoDataController(
                demoDataFiles,
                com.stacklens.application.service
                    .FakeI18n(),
                demoDataLocation = composeEnvironment,
            )

        controller.listDataFiles()

        verify(demoDataFiles).listDataFiles(composeFile)
    }

    @Test
    fun `a missing compose environment is reported as an unknown compose file for reads`() {
        whenever(demoDataFiles.readDataFile(anyOrNull(), any())).thenReturn(
            DockerDemoDataReadResult.ComposeNotFound,
        )
        val controller =
            DockerTestDemoDataController(
                demoDataFiles,
                com.stacklens.application.service
                    .FakeI18n(),
                demoDataLocation = mock(),
            )

        val response = controller.readDataFile("seed.sql")

        assertEquals(HttpStatus.BAD_REQUEST.value(), response.statusCode.value())
        assertEquals("docker_test.be_compose_not_found", response.body!!["error"])
    }
}
