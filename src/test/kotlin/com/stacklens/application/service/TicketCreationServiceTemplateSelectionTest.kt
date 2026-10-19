package com.stacklens.application.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.out.demo.DemoTicketSimulator
import com.stacklens.adapter.out.shared.JacksonJsonCodecAdapter
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.TicketCreationJiraPort
import com.stacklens.application.port.out.TicketCreationRecordPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.model.IssueType
import com.stacklens.domain.model.TicketCreationRecord
import com.stacklens.testsupport.FakeKnowledgePublicationPort
import com.stacklens.testsupport.NoDemoTicketCreation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TicketCreationServiceTemplateSelectionTest {
    private val copilot: CopilotPort = mock()
    private val promptReader: PromptReaderPort = mock()
    private val jiraConfigPort: JiraConfigPort = mock()
    private val jiraPort: TicketCreationJiraPort = mock()
    private val recordPort: TicketCreationRecordPort = mock()
    private val jsonCodec = JacksonJsonCodecAdapter(ObjectMapper())
    private lateinit var service: TicketCreationService

    @BeforeEach
    fun setUp() {
        service =
            TicketCreationService(
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
        whenever(copilot.resolveLanguage()).thenReturn("en")
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(promptReader.get(any(), any(), any())).thenReturn("the prompt")
        whenever(recordPort.saveTicketCreationRecord(any())).thenAnswer { it.arguments[0] as TicketCreationRecord }
        whenever(recordPort.findTicketCreationRecordById(any()))
            .thenReturn(TicketCreationRecord(id = 0L, status = "RUNNING"))
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull()))
            .thenReturn("""{"summary":"T","description":"D","issueType":"Story","labels":[]}""")
    }

    @Test
    fun `Story selection loads the create_story prompt key`() {
        service.generateTicket("Add SSO", issueType = IssueType.STORY)
        verify(promptReader).get(eq("en"), eq(IssueType.STORY.promptKey), any())
    }

    @Test
    fun `Technical Task selection loads the create_technical_task prompt key`() {
        service.generateTicket("Refactor auth module", issueType = IssueType.TECHNICAL_TASK)
        verify(promptReader).get(eq("en"), eq(IssueType.TECHNICAL_TASK.promptKey), any())
    }

    @Test
    fun `Bug selection loads the create_bug prompt key — the regression guard`() {
        service.generateTicket("Login form throws 500", issueType = IssueType.BUG)
        verify(promptReader).get(eq("en"), eq(IssueType.BUG.promptKey), any())
    }

    @Test
    fun `null issueType falls back to the default which is Story`() {
        service.generateTicket("Anything", issueType = null)
        verify(promptReader).get(eq("en"), eq(IssueType.DEFAULT.promptKey), any())
    }

    @Test
    fun `Story prompt receives inputText, attachmentSection and outputLanguage substitutions`() {
        service.generateTicket("My input", "## attachments", issueType = IssueType.STORY)

        verify(promptReader).get(
            eq("en"),
            eq(IssueType.STORY.promptKey),
            org.mockito.kotlin.check { vars ->
                assertEquals("My input", vars["inputText"])
                assertEquals("## attachments", vars["attachmentSection"])
                assertEquals("English", vars["outputLanguage"])
            },
        )
    }

    @Test
    fun `Bug prompt receives inputText, attachmentSection and outputLanguage substitutions`() {
        service.generateTicket("My bug", "## bug context", issueType = IssueType.BUG)

        verify(promptReader).get(
            eq("en"),
            eq(IssueType.BUG.promptKey),
            org.mockito.kotlin.check { vars ->
                assertEquals("My bug", vars["inputText"])
                assertEquals("## bug context", vars["attachmentSection"])
                assertEquals("English", vars["outputLanguage"])
            },
        )
    }

    @Test
    fun `KI result issueType is forced to the user choice regardless of KI echo`() {
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull()))
            .thenReturn("""{"summary":"T","description":"D","issueType":"Story","labels":[]}""")
        val result = service.generateTicket("Login form throws 500", issueType = IssueType.BUG)
        assertEquals(IssueType.BUG.displayName, result["issueType"])
    }
}
