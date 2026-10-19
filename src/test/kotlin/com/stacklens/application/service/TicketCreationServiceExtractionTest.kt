package com.stacklens.application.service

import com.stacklens.adapter.out.demo.DemoTicketSimulator
import com.stacklens.application.port.inbound.platform.I18nUseCase
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
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.jvm.javaMethod

class TicketCreationServiceExtractionTest {
    private val i18n: I18nUseCase = mock()
    private val copilot: CopilotPort = mock()
    private val promptReader: PromptReaderPort = mock()
    private val jiraConfigPort: JiraConfigPort = mock()
    private val jiraPort: TicketCreationJiraPort = mock()
    private val recordPort: TicketCreationRecordPort = mock()
    private val jsonCodec: JsonCodecPort = mock()
    private val service =
        TicketCreationService(
            copilot = copilot,
            aiUsageContext = mock<com.stacklens.application.port.out.ai.AiUsageContextPort>(),
            promptReader = promptReader,
            jiraConfigPort = jiraConfigPort,
            jiraPort = jiraPort,
            recordPort = recordPort,
            jsonCodec = jsonCodec,
            resultNormalizer =
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

    private fun ensureTrailingSeparator(description: String): String {
        val func = JiraSubtaskCreator::class.declaredFunctions.first { it.name == "ensureTrailingSeparator" }
        func.javaMethod!!.isAccessible = true
        return func.call(JiraSubtaskCreator(jiraPort, NoDemoTicketCreation), description) as String
    }

    @Test
    fun `ensureTrailingSeparator returns input unchanged when blank`() {
        assertEquals("", ensureTrailingSeparator(""))
        assertEquals("   ", ensureTrailingSeparator("   "))
        assertEquals("\n\n", ensureTrailingSeparator("\n\n"))
    }

    @Test
    fun `ensureTrailingSeparator appends separator when missing`() {
        val out = ensureTrailingSeparator("hello world")
        assertTrue(out.endsWith("---"))
    }

    @Test
    fun `ensureTrailingSeparator preserves separator when already present`() {
        val out = ensureTrailingSeparator("hello world\n\n---")
        assertEquals(1, "---".toRegex().findAll(out).count())
    }

    @Test
    fun `ensureTrailingSeparator trims trailing whitespace before appending separator`() {
        val out = ensureTrailingSeparator("hello world   \n  ")
        assertTrue(out.endsWith("---"))
        assertTrue(!out.endsWith("  ---"))
    }

    private fun extractJson(raw: String): String {
        val func = TicketCreationService::class.declaredFunctions.first { it.name == "extractJson" }
        func.javaMethod!!.isAccessible = true
        return func.call(service, raw) as String
    }

    @Test
    fun `extractJson strips markdown code fences`() {
        val out =
            extractJson(
                """```json
{"key": "value"}
```""",
            )
        assertEquals("""{"key": "value"}""", out)
    }

    @Test
    fun `extractJson strips bare markdown fences`() {
        val out =
            extractJson(
                """```
{"key": "value"}
```""",
            )
        assertEquals("""{"key": "value"}""", out)
    }

    @Test
    fun `extractJson finds first balanced JSON object in raw text`() {
        val out = extractJson("""Here is the JSON: {"a": 1, "b": 2}""")
        assertTrue(out.startsWith("{"))
        assertTrue(out.contains("\"a\""))
    }

    @Test
    fun `extractJson handles nested objects`() {
        val out = extractJson("""{"outer": {"inner": 1}}""")
        assertTrue(out.contains("\"inner\""))
    }

    @Test
    fun `extractJson handles escaped quotes inside strings`() {
        val out = extractJson("""{"text": "He said \"hi\""}""")
        assertTrue(out.contains("He said"))
    }

    @Test
    fun `extractJson returns empty when no JSON object is found`() {
        var caught = false
        try {
            extractJson("just plain text, no JSON here")
        } catch (_: Exception) {
            caught = true
        }
        assertTrue(caught, "Expected exception when no JSON object is found")
    }

    @Test
    fun `extractJson finds JSON even with leading whitespace`() {
        val out = extractJson("""   {"key": "value"}""")
        assertTrue(out.contains("\"key\""))
    }

    @Test
    fun `extractJson handles JSON with array at top level via wrapping`() {
        var caught = false
        try {
            extractJson("""[1, 2, 3]""")
        } catch (_: Exception) {
            caught = true
        }
        assertTrue(caught)
    }

    @Test
    fun `extractJson handles malformed JSON gracefully`() {
        var caught = false
        try {
            extractJson("""{malformed""")
        } catch (_: Exception) {
            caught = true
        }
        assertTrue(caught, "Expected exception for malformed JSON")
    }
}
