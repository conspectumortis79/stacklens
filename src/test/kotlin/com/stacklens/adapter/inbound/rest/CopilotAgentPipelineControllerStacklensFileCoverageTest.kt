package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.pipeline.CopilotAgentPipelineUseCase
import com.stacklens.application.port.inbound.ticket.JiraTicketImplementationUseCase
import com.stacklens.application.port.inbound.workspace.WorkspaceAccessView
import com.stacklens.application.port.inbound.workspace.WorkspaceFileUseCase
import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.testsupport.FakeI18n
import com.stacklens.testsupport.FakeSystemGuidanceUseCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CopilotAgentPipelineControllerStacklensFileCoverageTest {
    private val pipelineService: CopilotAgentPipelineUseCase = mock()
    private val workspaceFileUseCase: WorkspaceFileUseCase = mock()
    private val controller =
        CopilotAgentPipelineController(
            pipelineService,
            mock<JiraTicketImplementationUseCase>(),
            FakeI18n(),
            mock(),
            mock(),
            FakeSystemGuidanceUseCase(),
            workspaceFileUseCase,
        )

    private fun runWith(
        id: Long,
        path: String,
    ) {
        whenever(pipelineService.get(id)).thenReturn(
            AgentPipelineRun(id = id, rawText = "text", repoPath = path, featureSlug = "slug", createdAt = 0L),
        )
    }

    @Test
    fun `a probe failure caused by a blocker is reported with its error`() {
        runWith(4L, "/repo")
        whenever(workspaceFileUseCase.probeAccess("/repo")).thenReturn(
            WorkspaceAccessView("/repo", true, true, true, true, false, "not a directory"),
        )

        val body = controller.checkRepoAccess(4L).body!!

        assertEquals(false, body["ok"])
        assertEquals("i18n:config.be_write_probe_failed", body["reason"])
        assertEquals("not a directory", body["probeError"])
    }

    @Test
    fun `a successful probe with an existing stacklens directory is reported as ok`() {
        runWith(5L, "/repo")
        whenever(workspaceFileUseCase.probeAccess("/repo")).thenReturn(
            WorkspaceAccessView("/repo", true, true, true, true, true, null),
        )

        val body = controller.checkRepoAccess(5L).body!!

        assertEquals(true, body["ok"])
        assertEquals("/repo", body["path"])
    }
}
