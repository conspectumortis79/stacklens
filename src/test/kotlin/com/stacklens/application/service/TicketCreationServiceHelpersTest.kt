package com.stacklens.application.service

import com.stacklens.adapter.out.demo.DemoTicketSimulator
import com.stacklens.application.port.model.SubtaskDraft
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.port.out.TicketCreationJiraPort
import com.stacklens.application.port.out.TicketCreationRecordPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.testsupport.FakeKnowledgePublicationPort
import com.stacklens.testsupport.NoDemoTicketCreation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class TicketCreationServiceHelpersTest {
    private fun newService(): TicketCreationService {
        val copilot = mock<CopilotPort>()
        val promptReader = mock<PromptReaderPort>()
        val jiraConfigPort = mock<JiraConfigPort>()
        val jiraPort = mock<TicketCreationJiraPort>()
        val recordPort = mock<TicketCreationRecordPort>()
        val jsonCodec = mock<JsonCodecPort>()
        return TicketCreationService(
            copilot,
            mock<com.stacklens.application.port.out.ai.AiUsageContextPort>(),
            promptReader,
            jiraConfigPort,
            jiraPort,
            recordPort,
            jsonCodec,
            TicketCreationResultNormalizer(
                sectionStrategies =
                    listOf(
                        com.stacklens.application.service.ticketcreation
                            .TechnicalTaskSectionStrategy(),
                        com.stacklens.application.service.ticketcreation
                            .StoryBugSectionStrategy(),
                    ),
            ),
            aiJsonExtractor =
                com.stacklens.adapter.out.textfilter
                    .DefaultAiResponseJsonExtractor(),
            subtaskCreator = JiraSubtaskCreator(jiraPort, NoDemoTicketCreation),
            knowledgePublication = FakeKnowledgePublicationPort(),
            knowledgeMapper = mock<com.stacklens.application.service.knowledge.AnalysisKnowledgeMapper>(),
            jiraService =
                TicketCreationJiraService(
                    jiraConfigPort,
                    jiraPort,
                    DemoTicketSimulator(recordPort),
                    JiraSubtaskCreator(jiraPort, NoDemoTicketCreation),
                    recordPort,
                ),
            historyService = TicketCreationHistoryService(recordPort),
        )
    }

    private fun newDemoSimulator(): DemoTicketSimulator =
        DemoTicketSimulator(
            recordPort = mock<TicketCreationRecordPort>(),
            random = { 4242 },
        )

    @Test
    fun `simulateDemoTicket returns demo key and DEMO id when historyId is null`() {
        val sim = newDemoSimulator()
        val result =
            sim.simulate(
                jiraConfigId = 1L,
                projectKey = "abc",
                issueType = "Story",
                labels = listOf("a", "b"),
                historyId = null,
                configName = "configName",
                subtasks = emptyList(),
            )
        assertEquals("DEMO", result["id"])
        assertTrue((result["key"] as String).startsWith("ABC-"))
        assertEquals("", result["url"])
        assertTrue((result["subtasks"] as List<*>).isEmpty())
    }

    @Test
    fun `simulateDemoTicket returns deterministic key when historyId is non-null`() {
        val sim = newDemoSimulator()
        val result =
            sim.simulate(
                jiraConfigId = 1L,
                projectKey = "abc",
                issueType = "Story",
                labels = emptyList(),
                historyId = 42L,
                configName = "configName",
                subtasks = listOf(SubtaskDraft(summary = "do a thing", description = "d", labels = listOf("a"))),
            )
        assertEquals("ABC-1042", result["key"])
        assertEquals("/simulated-jira-ticket/42", result["url"])
        val subs = (result["subtasks"] as List<*>).map { requireNotNull(it) as Map<*, *> }
        assertEquals(1, subs.size)
        assertEquals("do a thing", subs[0]["summary"])
        assertEquals("Subtask", subs[0]["issueType"])
    }
}
