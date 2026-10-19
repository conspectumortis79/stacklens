package com.stacklens.adapter.inbound.rest.dockertest

import com.stacklens.application.port.inbound.dockertest.DockerDemoDataFileUseCase
import com.stacklens.application.port.inbound.dockertest.DockerDemoDataLocationUseCase
import com.stacklens.application.port.inbound.dockertest.DockerDemoDataReadResult
import com.stacklens.application.port.inbound.dockertest.DockerDemoDataWriteResult
import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.application.port.inbound.workspace.WorkspaceWriteRejection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus

class DockerTestDemoDataControllerMessagesTest {
    private val composeEnvironment: DockerDemoDataLocationUseCase = mock()
    private val i18n: I18nUseCase = mock()
    private val demoDataFiles: DockerDemoDataFileUseCase = mock()

    private val composeFile = "/srv/project/docker-compose.yml"

    private fun controller(): DockerTestDemoDataController = DockerTestDemoDataController(demoDataFiles, i18n = i18n, demoDataLocation = composeEnvironment)

    @Test
    fun `each read rejection reason is translated with its own key`() {
        whenever(composeEnvironment.composeFileLocation()).thenReturn(composeFile)
        whenever(i18n.t(any(), anyVararg())).thenAnswer { invocation -> "tr:" + invocation.getArgument<String>(0) }
        whenever(demoDataFiles.readDataFile(anyOrNull(), any())).thenReturn(
            DockerDemoDataReadResult.ComposeNotFound,
            DockerDemoDataReadResult.PathOutsideBase,
            DockerDemoDataReadResult.NotAFile,
            DockerDemoDataReadResult.ExtensionNotEditable,
        )

        assertEquals("tr:docker_test.be_compose_not_found", controller().readDataFile("seed.sql").body?.get("error"))
        assertEquals("tr:docker_test.be_invalid_path", controller().readDataFile("/etc/passwd").body?.get("error"))
        assertEquals("tr:docker_test.be_not_a_file", controller().readDataFile("/srv/project").body?.get("error"))
        assertEquals("tr:docker_test.be_extension_not_editable", controller().readDataFile("notes.txt").body?.get("error"))
    }

    @Test
    fun `each write rejection reason is translated with its own key`() {
        whenever(composeEnvironment.composeFileLocation()).thenReturn(composeFile)
        whenever(i18n.t(any(), anyVararg())).thenAnswer { invocation -> "tr:" + invocation.getArgument<String>(0) }
        whenever(demoDataFiles.writeDataFile(anyOrNull(), any(), any())).thenReturn(
            DockerDemoDataWriteResult.ComposeNotFound,
            DockerDemoDataWriteResult.Rejected(WorkspaceWriteRejection.PATH_OUTSIDE_ALLOWED_ROOT),
            DockerDemoDataWriteResult.Rejected(WorkspaceWriteRejection.EXTENSION_NOT_EDITABLE),
            DockerDemoDataWriteResult.Rejected(WorkspaceWriteRejection.WRITE_FAILED),
            DockerDemoDataWriteResult.Rejected(WorkspaceWriteRejection.TARGET_NOT_A_FILE),
        )

        assertEquals(
            "tr:docker_test.be_compose_not_found",
            controller().writeDataFile(DockerTestDemoDataController.DataFileWriteRequest("seed.sql", "x")).body?.get("error"),
        )
        assertEquals(
            "tr:docker_test.be_invalid_path",
            controller().writeDataFile(DockerTestDemoDataController.DataFileWriteRequest("/etc/passwd", "x")).body?.get("error"),
        )
        assertEquals(
            "tr:docker_test.be_extension_not_editable",
            controller().writeDataFile(DockerTestDemoDataController.DataFileWriteRequest("notes.txt", "x")).body?.get("error"),
        )
        assertEquals(
            "tr:docker_test.editor_save_error",
            controller().writeDataFile(DockerTestDemoDataController.DataFileWriteRequest("seed.sql", "x")).body?.get("error"),
        )
        assertEquals(
            "tr:docker_test.be_not_a_file",
            controller().writeDataFile(DockerTestDemoDataController.DataFileWriteRequest("gone.sql", "x")).body?.get("error"),
        )
    }

    @Test
    fun `an unmapped translation key is answered verbatim`() {
        whenever(composeEnvironment.composeFileLocation()).thenReturn(null)
        whenever(demoDataFiles.readDataFile(null, "seed.sql")).thenReturn(DockerDemoDataReadResult.ComposeNotFound)
        val untranslated =
            DockerTestDemoDataController(
                demoDataFiles,
                com.stacklens.application.service
                    .FakeI18n(),
                demoDataLocation = composeEnvironment,
            )

        val response = untranslated.readDataFile("seed.sql")

        assertEquals(HttpStatus.BAD_REQUEST.value(), response.statusCode.value())
        assertEquals("docker_test.be_compose_not_found", response.body?.get("error"))
    }
}
