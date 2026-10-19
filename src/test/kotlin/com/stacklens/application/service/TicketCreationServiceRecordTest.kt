package com.stacklens.application.service

import com.stacklens.adapter.out.textfilter.DefaultAiResponseJsonExtractor
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.port.out.TicketCreationJiraPort
import com.stacklens.application.port.out.TicketCreationRecordPort
import com.stacklens.application.port.out.ai.AiUsageContextPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.application.service.knowledge.AnalysisKnowledgeMapper
import com.stacklens.application.service.knowledge.KnowledgePublication
import com.stacklens.application.service.knowledge.KnowledgePublicationCandidate
import com.stacklens.domain.model.IssueType
import com.stacklens.domain.model.TicketCreationRecord
import com.stacklens.testsupport.NoDemoTicketCreation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TicketCreationServiceRecordTest {
    private val copilot = mock<CopilotPort>()

    private val promptReader = mock<PromptReaderPort>()

    private val jsonCodec = mock<JsonCodecPort>()

    private val recordPort = mock<TicketCreationRecordPort>()

    private val knowledgeMapper = mock<AnalysisKnowledgeMapper>()

    private val knowledgePublication = mock<KnowledgePublication>()

    private val normalizer = mock<TicketCreationResultNormalizer>()

    private fun service() =
        TicketCreationService(
            copilot = copilot,
            aiUsageContext = mock<AiUsageContextPort>(),
            promptReader = promptReader,
            jiraConfigPort = mock<JiraConfigPort>(),
            jiraPort = mock<TicketCreationJiraPort>(),
            recordPort = recordPort,
            jsonCodec = jsonCodec,
            resultNormalizer = normalizer,
            aiJsonExtractor = DefaultAiResponseJsonExtractor(),
            subtaskCreator = JiraSubtaskCreator(mock<TicketCreationJiraPort>(), NoDemoTicketCreation),
            knowledgePublication = knowledgePublication,
            knowledgeMapper = knowledgeMapper,
            jiraService = mock<TicketCreationJiraService>(),
            historyService = TicketCreationHistoryService(recordPort),
        )

    private fun stubGeneration(result: Map<String, Any?>) {
        whenever(copilot.resolveLanguage()).thenReturn("   ")
        whenever(copilot.resolveModel()).thenReturn("modell")
        whenever(promptReader.get(any(), any(), any())).thenReturn("prompt")
        whenever(copilot.execute(any(), any(), anyOrNull(), any())).thenReturn("""{"summary":"x"}""")
        whenever(jsonCodec.decodeLenientMap(any())).thenReturn(mutableMapOf("summary" to "x"))
        whenever(normalizer.normalize(any(), any())).thenReturn(result)
        whenever(recordPort.saveTicketCreationRecord(any())).thenAnswer { it.arguments[0] as TicketCreationRecord }
        whenever(recordPort.findTicketCreationRecordById(any())).thenReturn(TicketCreationRecord(id = 0L, status = "RUNNING"))
    }

    private val candidate = mock<KnowledgePublicationCandidate>()

    @Test
    fun `a blank language falls back to english and an unencodable result keeps the record json free`() {
        stubGeneration(mapOf("summary" to 5, "description" to "d"))
        whenever(jsonCodec.encode(any())).thenThrow(RuntimeException("serialisieren unmöglich"))
        whenever(knowledgeMapper.fromTicketCreation(any())).thenReturn(candidate)
        Mockito
            .doThrow(RuntimeException("wissen indexed nicht möglich"))
            .`when`(knowledgePublication)
            .publish(any())

        val result = service().generateTicket("baue etwas", "", IssueType.STORY)

        assertEquals(5, result["summary"])
        verify(recordPort).saveTicketCreationRecord(
            org.mockito.kotlin.argThat { record -> record.resultJson == null && record.summary == null && record.status == "COMPLETED" },
        )
    }

    @Test
    fun `a record without a knowledge candidate skips the publication`() {
        stubGeneration(mapOf("summary" to "text", "description" to "d"))
        whenever(jsonCodec.encode(any())).thenReturn("""{"summary":"text"}""")
        whenever(knowledgeMapper.fromTicketCreation(any())).thenReturn(null)

        service().generateTicket("baue etwas", "", IssueType.BUG)

        verify(knowledgePublication, org.mockito.Mockito.never()).publish(any())
        verify(recordPort).saveTicketCreationRecord(
            org.mockito.kotlin.argThat { record -> record.summary == "text" && record.resultJson == """{"summary":"text"}""" },
        )
    }

    @Test
    fun `a publishable record is handed to the knowledge publication`() {
        stubGeneration(mapOf("summary" to "text"))
        whenever(jsonCodec.encode(any())).thenReturn("{}")
        whenever(knowledgeMapper.fromTicketCreation(any())).thenReturn(candidate)

        service().generateTicket("baue etwas", "", IssueType.TECHNICAL_TASK)

        verify(knowledgePublication).publish(candidate)
    }

    @Test
    fun `an exception without a message still fails the record`() {
        whenever(copilot.resolveLanguage()).thenReturn("de")
        whenever(copilot.resolveModel()).thenReturn("modell")
        whenever(promptReader.get(any(), any(), any())).thenReturn("prompt")
        whenever(copilot.execute(any(), any(), anyOrNull(), any())).thenThrow(RuntimeException())
        whenever(recordPort.saveTicketCreationRecord(any())).thenAnswer { it.arguments[0] as TicketCreationRecord }
        whenever(recordPort.findTicketCreationRecordById(any())).thenReturn(TicketCreationRecord(id = 3L, status = "RUNNING"))

        assertThrows(RuntimeException::class.java) {
            service().generateTicket("baue etwas", "", null)
        }

        verify(recordPort).saveTicketCreationRecord(
            org.mockito.kotlin.argThat { record -> record.status == "FAILED" && record.errorMessage == null },
        )
    }
}
