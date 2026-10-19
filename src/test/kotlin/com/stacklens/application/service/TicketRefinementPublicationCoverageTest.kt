package com.stacklens.application.service

import com.stacklens.application.port.out.DatabaseConfigPort
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.TicketRefinementPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.database.DatabaseSchemaPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.application.service.knowledge.AnalysisKnowledgeMapper
import com.stacklens.application.service.knowledge.KnowledgePublication
import com.stacklens.application.service.knowledge.KnowledgePublicationCandidate
import com.stacklens.application.service.ticketrefinement.TicketRefinementCollaborators
import com.stacklens.application.service.ticketrefinement.TicketRefinementDeps
import com.stacklens.domain.model.TicketRefinement
import com.stacklens.testsupport.InlineAsyncExecutor
import com.stacklens.testsupport.PrivateInvocation
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TicketRefinementPublicationCoverageTest {
    private val repo = mock<TicketRefinementPort>()

    private val knowledgePublication = mock<KnowledgePublication>()

    private val knowledgeMapper = mock<AnalysisKnowledgeMapper>()

    private val copilot = mock<CopilotPort>()

    private fun service(): TicketRefinementService {
        val deps =
            TicketRefinementDeps(
                ticketRefinementRepo = repo,
                jiraConfigRepo = mock<JiraConfigPort>(),
                jira = mock(),
                copilot = copilot,
                aiUsageContext = mock(),
                promptLoader = mock<PromptReaderPort>(),
                jsonCodec =
                    com.stacklens.testsupport.TestJsonCodec
                        .create(),
                i18n = com.stacklens.testsupport.FakeI18n(),
                dbConfigRepo = mock<DatabaseConfigPort>(),
                dbSchemaPort = mock<DatabaseSchemaPort>(),
                asyncExecutor = InlineAsyncExecutor(),
                startupHealer = mock(),
            )
        return TicketRefinementService(
            deps = deps,
            knowledgePublication = knowledgePublication,
            knowledgeMapper = knowledgeMapper,
            collaborators = mock<TicketRefinementCollaborators>(),
            clock = com.stacklens.adapter.out.time.SystemClockPort,
        )
    }

    private fun entity() =
        TicketRefinement(
            id = 5L,
            parentTicketKey = "P-1",
            reviewsJson = "[]",
        )

    init {
        whenever(copilot.resolveLanguage()).thenReturn("de")
    }

    @Test
    fun `the finished run is published to the knowledge base`() {
        val candidate = mock<KnowledgePublicationCandidate>()
        whenever(knowledgeMapper.fromTicketRefinement(any())).thenReturn(listOf(candidate))

        PrivateInvocation.invoke(service(), "finalizeCompletedRun", entity())

        verify(repo).saveTicketRefinement(any())
        verify(knowledgePublication).publish(candidate)
    }

    @Test
    fun `a failing publication is swallowed and only logged`() {
        whenever(knowledgeMapper.fromTicketRefinement(any())).thenThrow(IllegalStateException("index is closed"))

        PrivateInvocation.invoke(service(), "finalizeCompletedRun", entity())

        verify(repo).saveTicketRefinement(any())
    }
}
