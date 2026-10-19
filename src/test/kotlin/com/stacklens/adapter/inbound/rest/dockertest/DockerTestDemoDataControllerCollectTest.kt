package com.stacklens.adapter.inbound.rest.dockertest

import com.stacklens.application.port.inbound.dockertest.DockerDemoDataFileEntry
import com.stacklens.application.port.inbound.dockertest.DockerDemoDataFileUseCase
import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.testsupport.FakeDockerDemoDataLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DockerTestDemoDataControllerCollectTest {
    private val demoDataFiles: DockerDemoDataFileUseCase = mock()

    private fun newController(
        i18n: I18nUseCase =
            com.stacklens.application.service
                .FakeI18n(),
        composeFile: String? = null,
    ): DockerTestDemoDataController =
        DockerTestDemoDataController(
            demoDataFiles = demoDataFiles,
            i18n = i18n,
            demoDataLocation = FakeDockerDemoDataLocation(composeFile),
        )

    @Test
    fun `DataFileWriteRequest defaults populate sensible values`() {
        val r = DockerTestDemoDataController.DataFileWriteRequest()
        assertEquals("", r.path)
        assertEquals("", r.content)
    }

    @Test
    fun `DataFileWriteRequest named constructor populates all fields`() {
        val r = DockerTestDemoDataController.DataFileWriteRequest(path = "p", content = "c")
        assertEquals("p", r.path)
        assertEquals("c", r.content)
    }

    @Test
    fun `listDataFiles maps typed entries to path and label`() {
        whenever(demoDataFiles.listDataFiles(anyOrNull())).thenReturn(
            listOf(
                DockerDemoDataFileEntry(path = "/base/seed.sql", label = "seed.sql"),
                DockerDemoDataFileEntry(path = "/base/sub/data.json", label = "sub/data.json"),
            ),
        )

        val response = newController().listDataFiles()

        assertEquals(200, response.statusCode.value())
        assertEquals(
            listOf(
                mapOf("path" to "/base/seed.sql", "label" to "seed.sql"),
                mapOf("path" to "/base/sub/data.json", "label" to "sub/data.json"),
            ),
            response.body,
        )
    }

    @Test
    fun `listDataFiles forwards the compose file location`() {
        val composeFile = "/srv/project/docker-compose.yml"
        whenever(demoDataFiles.listDataFiles(composeFile)).thenReturn(emptyList())

        newController(composeFile = composeFile).listDataFiles()

        verify(demoDataFiles).listDataFiles(composeFile)
    }

    @Test
    fun `listDataFiles returns an empty list without a compose file`() {
        whenever(demoDataFiles.listDataFiles(anyOrNull())).thenReturn(emptyList())

        val response = newController().listDataFiles()

        assertEquals(200, response.statusCode.value())
        assertEquals(emptyList<Map<String, String>>(), response.body)
    }
}
