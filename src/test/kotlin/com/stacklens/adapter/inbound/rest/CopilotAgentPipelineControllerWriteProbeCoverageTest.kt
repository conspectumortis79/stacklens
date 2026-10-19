package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.pipeline.CopilotAgentPipelineUseCase
import com.stacklens.application.port.inbound.ticket.JiraTicketImplementationUseCase
import com.stacklens.application.port.inbound.workspace.WorkspaceAccessView
import com.stacklens.application.port.inbound.workspace.WorkspaceFileUseCase
import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.testsupport.FakeI18n
import com.stacklens.testsupport.FakeSystemGuidanceUseCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CopilotAgentPipelineControllerWriteProbeCoverageTest {
    private val pipelineService: CopilotAgentPipelineUseCase = mock()
    private val workspaceFileUseCase: WorkspaceFileUseCase = mock()
    private val controller =
        CopilotAgentPipelineController(
            pipelineService = pipelineService,
            jiraAnalysisService = mock<JiraTicketImplementationUseCase>(),
            i18n = FakeI18n(),
            mockupReader = mock(),
            ssePublisher = mock(),
            systemGuidance = FakeSystemGuidanceUseCase(),
            workspaceFileUseCase = workspaceFileUseCase,
        )

    private fun runWith(
        id: Long,
        repoPath: String?,
    ) {
        whenever(pipelineService.get(id)).thenReturn(
            AgentPipelineRun(id = id, rawText = "text", repoPath = repoPath, featureSlug = "slug", createdAt = 0L),
        )
    }

    @Test
    fun `a successful probe is reported with the access view details`() {
        runWith(1L, "/repo")
        whenever(workspaceFileUseCase.probeAccess("/repo")).thenReturn(
            WorkspaceAccessView("/repo", true, true, true, true, true, null),
        )

        val body = controller.checkRepoAccess(1L).body!!

        assertEquals(true, body["ok"])
        assertEquals("/repo", body["path"])
        assertFalse(body.containsKey("probeError"))
    }

    @Test
    fun `a failed probe keeps the probe error and reports the write probe failure`() {
        runWith(2L, "/repo")
        whenever(workspaceFileUseCase.probeAccess("/repo")).thenReturn(
            WorkspaceAccessView("/repo", true, true, true, true, false, "mkdirs denied"),
        )

        val body = controller.checkRepoAccess(2L).body!!

        assertEquals(false, body["ok"])
        assertEquals("i18n:config.be_write_probe_failed", body["reason"])
        assertEquals("mkdirs denied", body["probeError"])
    }

    @Test
    fun `a run without a repository path is reported without probing`() {
        whenever(pipelineService.get(3L)).thenReturn(
            AgentPipelineRun(id = 3L, rawText = "text", repoPath = null, featureSlug = "slug", createdAt = 0L),
        )

        val body = controller.checkRepoAccess(3L).body!!

        assertEquals(false, body["ok"])
        assertEquals("i18n:config.be_no_repo_linked", body["reason"])
    }
}
