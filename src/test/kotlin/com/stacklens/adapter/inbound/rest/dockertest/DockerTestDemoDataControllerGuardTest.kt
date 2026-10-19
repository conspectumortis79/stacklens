package com.stacklens.adapter.inbound.rest.dockertest

import com.stacklens.application.port.inbound.dockertest.DockerDemoDataFileUseCase
import com.stacklens.application.port.inbound.dockertest.DockerDemoDataReadResult
import com.stacklens.application.port.inbound.dockertest.DockerDemoDataWriteResult
import com.stacklens.application.port.inbound.workspace.WorkspaceWriteRejection
import com.stacklens.testsupport.FakeDockerDemoDataLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DockerTestDemoDataControllerGuardTest {
    private val demoDataFiles: DockerDemoDataFileUseCase = mock()

    private val composeFile = "/srv/project/docker-compose.yml"

    private fun controller(composeLocation: String? = composeFile): DockerTestDemoDataController =
        DockerTestDemoDataController(
            demoDataFiles = demoDataFiles,
            i18n =
                com.stacklens.application.service
                    .FakeI18n(),
            demoDataLocation = FakeDockerDemoDataLocation(composeLocation),
        )

    @Test
    fun `readDataFile returns the content of a readable file`() {
        whenever(demoDataFiles.readDataFile(composeFile, "/srv/project/seed.sql")).thenReturn(
            DockerDemoDataReadResult.Read("select 1"),
        )

        val response = controller().readDataFile("/srv/project/seed.sql")

        assertEquals(200, response.statusCode.value())
        assertEquals("select 1", response.body!!["content"])
    }

    @Test
    fun `readDataFile reports a missing compose file`() {
        whenever(demoDataFiles.readDataFile(null, "/srv/project/seed.sql")).thenReturn(
            DockerDemoDataReadResult.ComposeNotFound,
        )

        val response = controller(composeLocation = null).readDataFile("/srv/project/seed.sql")

        assertEquals(400, response.statusCode.value())
        assertEquals("docker_test.be_compose_not_found", response.body!!["error"])
    }

    @Test
    fun `readDataFile reports paths outside the compose directory`() {
        whenever(demoDataFiles.readDataFile(composeFile, "/etc/passwd")).thenReturn(
            DockerDemoDataReadResult.PathOutsideBase,
        )

        val response = controller().readDataFile("/etc/passwd")

        assertEquals(400, response.statusCode.value())
        assertEquals("docker_test.be_invalid_path", response.body!!["error"])
    }

    @Test
    fun `readDataFile reports directories and missing files`() {
        whenever(demoDataFiles.readDataFile(eq(composeFile), any())).thenReturn(
            DockerDemoDataReadResult.NotAFile,
        )

        val response = controller().readDataFile("/srv/project")

        assertEquals(400, response.statusCode.value())
        assertEquals("docker_test.be_not_a_file", response.body!!["error"])
    }

    @Test
    fun `readDataFile reports non editable extensions`() {
        whenever(demoDataFiles.readDataFile(eq(composeFile), any())).thenReturn(
            DockerDemoDataReadResult.ExtensionNotEditable,
        )

        val response = controller().readDataFile("/srv/project/notes.txt")

        assertEquals(400, response.statusCode.value())
        assertEquals("docker_test.be_extension_not_editable", response.body!!["error"])
    }

    @Test
    fun `writeDataFile forwards path and content and reports success`() {
        whenever(
            demoDataFiles.writeDataFile(composeFile, "/srv/project/seed.sql", "select 2"),
        ).thenReturn(DockerDemoDataWriteResult.Saved("/srv/project/seed.sql"))

        val response =
            controller().writeDataFile(
                DockerTestDemoDataController.DataFileWriteRequest(path = "/srv/project/seed.sql", content = "select 2"),
            )

        assertEquals(200, response.statusCode.value())
        assertEquals("ok", response.body!!["status"])
        verify(demoDataFiles).writeDataFile(composeFile, "/srv/project/seed.sql", "select 2")
    }

    @Test
    fun `writeDataFile reports a missing compose file`() {
        whenever(demoDataFiles.writeDataFile(anyOrNull(), any(), any())).thenReturn(
            DockerDemoDataWriteResult.ComposeNotFound,
        )

        val response =
            controller(composeLocation = null).writeDataFile(
                DockerTestDemoDataController.DataFileWriteRequest(path = "seed.sql", content = "x"),
            )

        assertEquals(400, response.statusCode.value())
        assertEquals("docker_test.be_compose_not_found", response.body!!["error"])
    }

    @Test
    fun `writeDataFile maps each rejection reason`() {
        whenever(demoDataFiles.writeDataFile(anyOrNull(), any(), any())).thenReturn(
            DockerDemoDataWriteResult.Rejected(WorkspaceWriteRejection.PATH_OUTSIDE_ALLOWED_ROOT),
            DockerDemoDataWriteResult.Rejected(WorkspaceWriteRejection.EXTENSION_NOT_EDITABLE),
            DockerDemoDataWriteResult.Rejected(WorkspaceWriteRejection.WRITE_FAILED),
            DockerDemoDataWriteResult.Rejected(WorkspaceWriteRejection.DIRECTORY_NOT_FOUND),
            DockerDemoDataWriteResult.Rejected(WorkspaceWriteRejection.TARGET_NOT_A_FILE),
        )

        assertEquals(
            "docker_test.be_invalid_path",
            controller().writeDataFile(DockerTestDemoDataController.DataFileWriteRequest("/etc/passwd", "x")).body!!["error"],
        )
        assertEquals(
            "docker_test.be_extension_not_editable",
            controller().writeDataFile(DockerTestDemoDataController.DataFileWriteRequest("notes.txt", "x")).body!!["error"],
        )
        assertEquals(
            "docker_test.editor_save_error",
            controller().writeDataFile(DockerTestDemoDataController.DataFileWriteRequest("seed.sql", "x")).body!!["error"],
        )
        assertEquals(
            "docker_test.be_not_a_file",
            controller().writeDataFile(DockerTestDemoDataController.DataFileWriteRequest("missing/seed.sql", "x")).body!!["error"],
        )
        assertEquals(
            "docker_test.be_not_a_file",
            controller().writeDataFile(DockerTestDemoDataController.DataFileWriteRequest("gone.sql", "x")).body!!["error"],
        )
    }
}
