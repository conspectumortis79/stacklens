package com.stacklens.application.service

import com.stacklens.application.port.out.DatabaseConfigPort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.application.port.out.TicketRefinementJiraPort
import com.stacklens.application.port.out.TicketRefinementPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.database.DatabaseSchemaPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.application.port.out.repo.GitlabPort
import com.stacklens.application.port.out.repo.LocalRepoScanPort
import com.stacklens.application.service.StartupHealer
import com.stacklens.application.service.ticketrefinement.TicketRefinementDeps
import com.stacklens.testsupport.FakeKnowledgePublicationPort
import com.stacklens.testsupport.InlineAsyncExecutor
import com.stacklens.testsupport.testCollaborators
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class TicketRefinementServiceDepsTest {
    private val ticketRefinementRepo = mock<TicketRefinementPort>()
    private val jiraConfigRepo = mock<JiraConfigPort>()
    private val jira = mock<TicketRefinementJiraPort>()
    private val copilot = mock<CopilotPort>()
    private val promptLoader = mock<PromptReaderPort>()
    private val jsonCodec = mock<JsonCodecPort>()
    private val i18n = mock<I18nPort>()
    private val localRepoConfigRepo = mock<LocalRepoConfigPort>()
    private val gitlabConfigRepo = mock<GitlabConfigPort>()
    private val localRepoScan = mock<LocalRepoScanPort>()
    private val gitlab = mock<GitlabPort>()
    private val dbConfigRepo = mock<DatabaseConfigPort>()
    private val dbSchemaPort = mock<DatabaseSchemaPort>()
    private val asyncExecutor = InlineAsyncExecutor()
    private val aiJsonRepairer = mock<com.stacklens.application.port.out.AiJsonRepairerPort>()
    private val aiJsonExtractor = mock<com.stacklens.application.port.out.AiResponseJsonExtractorPort>()

    private fun depsBundle(): TicketRefinementDeps =
        TicketRefinementDeps(
            ticketRefinementRepo = ticketRefinementRepo,
            jiraConfigRepo = jiraConfigRepo,
            jira = jira,
            copilot = copilot,
            aiUsageContext = mock<com.stacklens.application.port.out.ai.AiUsageContextPort>(),
            promptLoader = promptLoader,
            jsonCodec = jsonCodec,
            i18n = i18n,
            dbConfigRepo = dbConfigRepo,
            dbSchemaPort = dbSchemaPort,
            asyncExecutor = asyncExecutor,
            startupHealer = StartupHealer(),
        )

    @Test
    fun `primary deps-bundle constructor accepts every field from the bundle`() {
        val deps = depsBundle()
        val svc =
            TicketRefinementService(
                deps,
                knowledgePublication = FakeKnowledgePublicationPort(),
                knowledgeMapper = mock<com.stacklens.application.service.knowledge.AnalysisKnowledgeMapper>(),
                collaborators = deps.testCollaborators(localRepoConfigRepo, gitlabConfigRepo, localRepoScan, gitlab, aiJsonRepairer, aiJsonExtractor),
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            )

        assertEquals(deps.javaClass.declaredFields.size, COUNT_OF_DEPS_FIELDS_FORWARDED)

        assertEquals(ticketRefinementRepo, deps.ticketRefinementRepo)
        assertEquals(jira, deps.jira)
    }

    private companion object {
        const val COUNT_OF_DEPS_FIELDS_FORWARDED = 12
    }
}
