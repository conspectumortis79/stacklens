package com.stacklens.application.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.out.demo.DemoTicketSimulator
import com.stacklens.adapter.out.shared.JacksonJsonCodecAdapter
import com.stacklens.application.port.model.SubtaskDraft
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.TicketCreationJiraPort
import com.stacklens.application.port.out.TicketCreationRecordPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.model.CreatedJiraTicket
import com.stacklens.domain.model.IssueType
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.TicketCreationRecord
import com.stacklens.testsupport.FakeKnowledgePublicationPort
import com.stacklens.testsupport.NoDemoTicketCreation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TicketCreationServiceTest {
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
        whenever(recordPort.findTicketCreationRecordById(any())).thenReturn(TicketCreationRecord(id = 0L, status = "RUNNING"))
    }

    private fun jiraConfig(cloudMode: Boolean = true) =
        JiraConfig(
            id = 1L,
            name = "Test Jira",
            baseUrl = "https://jira.example.com",
            username = "user",
            apiToken = "token",
            cloudMode = cloudMode,
        )

    @Test
    fun `generateTicket parses standard quoted JSON from AI response`() {
        val json = """{"summary":"Fix login bug","description":"As a user...","issueType":"Bug","labels":[]}"""
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(json)

        val result = service.generateTicket("Fix the login bug")
        assertEquals("Fix login bug", result["summary"])
        assertEquals(IssueType.DEFAULT.displayName, result["issueType"])
    }

    @Test
    fun `generateTicket adds real subtask drafts when Story acceptance criteria contain multiple work packages`() {
        val json =
            """
            {
                "summary":"Add login",
                "description":"## 🧑‍💻 User Story\nAs a user I want to log in.\n\n---\n\n## ✅ Acceptance Criteria\n- Backend validates credentials\n- UI displays login errors\n\n---",
                "issueType":"Story",
                "priority":"Minor",
                "labels":[],
                "subtasks":[]
            }
            """.trimIndent()
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(json)

        val result = service.generateTicket("Add a login")

        @Suppress("UNCHECKED_CAST")
        val subtasks = result["subtasks"] as List<Map<String, Any?>>
        assertEquals(2, subtasks.size)
        assertEquals("Backend validates credentials", subtasks[0]["summary"])
        assertEquals("Subtask", subtasks[0]["issueType"])
    }

    @Test
    fun `generateTicket parses JSON with unquoted field names (lenient mapper)`() {
        val json = """{summary:"Fix login bug",description:"Details",issueType:"Story",labels:[]}"""
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(json)

        val result = service.generateTicket("Fix login bug")
        assertEquals("Fix login bug", result["summary"])
    }

    @Test
    fun `generateTicket parses JSON with single-quoted strings (lenient mapper)`() {
        val json = """{'summary':'Create feature','description':'Details','issueType':'Story','labels':[]}"""
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(json)

        val result = service.generateTicket("Create feature")
        assertEquals("Create feature", result["summary"])
    }

    @Test
    fun `generateTicket strips markdown code fence before parsing`() {
        val json = "```json\n{\"summary\":\"Ticket\",\"description\":\"Desc\",\"issueType\":\"Story\",\"labels\":[]}\n```"
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(json)

        val result = service.generateTicket("Create ticket")
        assertEquals("Ticket", result["summary"])
    }

    @Test
    fun `generateTicket strips plain code fence before parsing`() {
        val json = "```\n{\"summary\":\"Ticket\",\"description\":\"Desc\",\"issueType\":\"Story\",\"labels\":[]}\n```"
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(json)

        val result = service.generateTicket("Create ticket")
        assertEquals("Ticket", result["summary"])
    }

    @Test
    fun `generateTicket skips preamble brace and finds real JSON object`() {
        val response = """Here is the result: {invalid} {"summary":"Real Ticket","description":"Desc","issueType":"Story","labels":[]}"""
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(response)

        val result = service.generateTicket("Create real ticket")
        assertEquals("Real Ticket", result["summary"])
    }

    @Test
    fun `generateTicket handles nested JSON objects in description`() {
        val json =
            """{"summary":"Deploy feature","description":"Details here",""" +
                """"issueType":"Story","labels":["backend"],"acceptanceCriteria":["AC1","AC2"]}"""
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(json)

        val result = service.generateTicket("Deploy feature")
        assertEquals("Deploy feature", result["summary"])
        @Suppress("UNCHECKED_CAST")
        val labels = result["labels"] as? List<*>
        assertNotNull(labels)
        assertEquals(1, labels?.size)
    }

    @Test
    fun `generateTicket throws when AI response contains no JSON object`() {
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn("Sorry, I cannot help with that.")

        assertThrows<IllegalArgumentException> {
            service.generateTicket("Input text")
        }
    }

    @Test
    fun `generateTicket throws when AI response is empty`() {
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn("")

        assertThrows<IllegalArgumentException> {
            service.generateTicket("Input text")
        }
    }

    @Test
    fun `generateTicket uses German output language when copilot lang is de`() {
        whenever(copilot.resolveLanguage()).thenReturn("de")
        val json = """{"summary":"Bug beheben","description":"Details","issueType":"Bug","labels":[]}"""
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(json)

        val result = service.generateTicket("Bug beheben")
        assertEquals("Bug beheben", result["summary"])
    }

    @Test
    fun `generateTicket passes attachmentSection to promptReader`() {
        val json = """{"summary":"Attach","description":"Desc","issueType":"Story","labels":[]}"""
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(json)

        service.generateTicket("Create ticket", "## Attachments\nfile.pdf")

        org.mockito.kotlin.verify(promptReader).get(
            eq("en"),
            eq(IssueType.STORY.promptKey),
            org.mockito.kotlin.check { vars ->
                assertEquals("## Attachments\nfile.pdf", vars["attachmentSection"])
            },
        )
    }

    @Test
    fun `generateTicket handles JSON with nested braces inside string values`() {
        val json = """{"summary":"Fix {braces} issue","description":"When {x} happens","issueType":"Bug","labels":[]}"""
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(json)

        val result = service.generateTicket("Fix braces")
        assertEquals("Fix {braces} issue", result["summary"])
    }

    @Test
    fun `generateTicket handles JSON when preamble has balanced braces without colon`() {
        val response = "{abc}\n{\"summary\":\"Real\",\"description\":\"D\",\"issueType\":\"Story\",\"labels\":[]}"
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(response)

        val result = service.generateTicket("Real ticket")
        assertEquals("Real", result["summary"])
    }

    @Test
    fun `jiraConfigExists returns true when config is found`() {
        whenever(jiraConfigPort.findJiraConfigById(1L)).thenReturn(jiraConfig())
        assertTrue(service.jiraConfigExists(1L))
    }

    @Test
    fun `jiraConfigExists returns false when config is not found`() {
        whenever(jiraConfigPort.findJiraConfigById(999L)).thenReturn(null)
        assertFalse(service.jiraConfigExists(999L))
    }

    @Test
    fun `createJiraTicket throws when jira config is not found`() {
        whenever(jiraConfigPort.findJiraConfigById(42L)).thenReturn(null)

        val ex =
            assertThrows<IllegalArgumentException> {
                service.createJiraTicket(42L, "PROJ", "Summary", "Desc", "Story", emptyList())
            }
        assertTrue(ex.message!!.contains("42"))
    }

    @Test
    fun `createJiraTicket delegates to port and maps result to response map`() {
        val config = jiraConfig(cloudMode = true)
        whenever(jiraConfigPort.findJiraConfigById(1L)).thenReturn(config)
        whenever(jiraPort.createTicket(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(CreatedJiraTicket(key = "PROJ-42", id = "10001", url = "https://jira.example.com/browse/PROJ-42"))

        val result = service.createJiraTicket(1L, "PROJ", "My ticket", "Description here", "Story", listOf("backend"))

        assertEquals("PROJ-42", result["key"])
        assertEquals("10001", result["id"])
        assertTrue((result["url"] as String).contains("PROJ-42"))
    }

    @Test
    fun `createJiraTicket passes all parameters to port`() {
        val config = jiraConfig(cloudMode = false)
        whenever(jiraConfigPort.findJiraConfigById(1L)).thenReturn(config)
        whenever(jiraPort.createTicket(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(CreatedJiraTicket(key = "PROJ-43", id = "10002", url = "https://jira.example.com/browse/PROJ-43"))

        service.createJiraTicket(1L, "PROJ", "Server ticket", "Plain description", "Bug", listOf("urgent"))

        verify(jiraPort).createTicket(
            eq(config),
            eq("PROJ"),
            eq("Server ticket"),
            eq("Plain description"),
            eq("Bug"),
            eq(listOf("urgent")),
            any(),
            any(),
        )
    }

    @Test
    fun `createJiraTicket forwards custom fields to the port`() {
        val config = jiraConfig()
        val customFields = mapOf<String, Any>("customfield_10120" to "Tax", "customfield_10108" to listOf("Live"))
        whenever(jiraConfigPort.findJiraConfigById(1L)).thenReturn(config)
        whenever(jiraPort.createTicket(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(CreatedJiraTicket(key = "PROJ-44", id = "1", url = "u"))

        service.createJiraTicket(1L, "PROJ", "S", "D", "Bug", emptyList(), customFields = customFields)

        verify(jiraPort).createTicket(eq(config), eq("PROJ"), eq("S"), eq("D"), eq("Bug"), eq(emptyList()), any(), eq(customFields))
    }

    @Test
    fun `requiredJiraFields asks the port for the project issue type`() {
        val config = jiraConfig()
        val fields =
            listOf(
                com.stacklens.domain.model
                    .JiraCreateField("customfield_1", "Dept", false, listOf("A")),
            )
        whenever(jiraConfigPort.findJiraConfigById(1L)).thenReturn(config)
        whenever(jiraPort.requiredCreateFields(config, "PROJ", "Bug")).thenReturn(fields)

        assertEquals(fields, service.requiredJiraFields(1L, "PROJ", "Bug"))
    }

    @Test
    fun `requiredJiraFields reports a jira configuration that disappeared`() {
        whenever(jiraConfigPort.findJiraConfigById(7L)).thenReturn(null)

        val failure = assertThrows<IllegalArgumentException> { service.requiredJiraFields(7L, "PROJ", "Bug") }

        assertEquals("Jira configuration not found: 7", failure.message)
        verify(jiraPort, org.mockito.kotlin.never()).requiredCreateFields(any(), any(), any())
    }

    @Test
    fun `requiredJiraFields returns nothing for demo configurations without calling Jira`() {
        whenever(jiraConfigPort.findJiraConfigById(1L))
            .thenReturn(JiraConfig(id = 1L, name = "Demo Jira", baseUrl = "https://jira.example.com", apiToken = "token"))

        assertEquals(emptyList<Any>(), service.requiredJiraFields(1L, "PROJ", "Bug"))
        verify(jiraPort, org.mockito.kotlin.never()).requiredCreateFields(any(), any(), any())
    }

    @Test
    fun `createJiraTicket returns empty url when port returns empty key`() {
        val config = jiraConfig(cloudMode = false)
        whenever(jiraConfigPort.findJiraConfigById(1L)).thenReturn(config)
        whenever(jiraPort.createTicket(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(CreatedJiraTicket(key = "", id = "10006", url = ""))

        val result = service.createJiraTicket(1L, "PROJ", "No key ticket", "Desc", "Story", emptyList())

        assertEquals("", result["key"])
        assertEquals("", result["url"])
    }

    @Test
    fun `generateTicket includes historyId from saved record in result`() {
        val json = """{"summary":"Ticket","description":"Desc","issueType":"Story","labels":[]}"""
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(json)
        whenever(recordPort.saveTicketCreationRecord(any()))
            .thenReturn(TicketCreationRecord(id = 7L, summary = "Ticket", issueType = "Story"))

        val result = service.generateTicket("Create a ticket")

        assertEquals(7L, result["historyId"])
    }

    @Test
    fun `generateTicket saves record with summary and labels from AI result and default issueType`() {
        val json = """{"summary":"My Feature","description":"Desc","issueType":"Story","labels":["backend"]}"""
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(json)

        service.generateTicket("Create feature")

        verify(recordPort).saveTicketCreationRecord(
            org.mockito.kotlin.check { rec ->
                assertEquals("My Feature", rec.summary)
                assertEquals(IssueType.DEFAULT.displayName, rec.issueType)
                assertEquals("backend", rec.labels)
                assertNotNull(rec.resultJson)
            },
        )
    }

    @Test
    fun `createJiraTicket updates history record when historyId is given`() {
        val existing = TicketCreationRecord(id = 5L, summary = "Ticket", issueType = "Story")
        val config = jiraConfig(cloudMode = true)
        whenever(jiraConfigPort.findJiraConfigById(1L)).thenReturn(config)
        whenever(jiraPort.createTicket(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(CreatedJiraTicket(key = "PROJ-99", id = "9999", url = "https://jira.example.com/browse/PROJ-99"))
        whenever(recordPort.findTicketCreationRecordById(5L)).thenReturn(existing)

        service.createJiraTicket(1L, "PROJ", "Ticket", "Desc", "Story", emptyList(), historyId = 5L)

        verify(recordPort).saveTicketCreationRecord(
            org.mockito.kotlin.check { rec ->
                assertEquals(5L, rec.id)
                assertEquals("PROJ-99", rec.jiraKey)
                assertEquals("PROJ", rec.jiraProjectKey)
            },
        )
    }

    @Test
    fun `createJiraTicket skips history update when historyId is null`() {
        val config = jiraConfig()
        whenever(jiraConfigPort.findJiraConfigById(1L)).thenReturn(config)
        whenever(jiraPort.createTicket(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(CreatedJiraTicket(key = "PROJ-1", id = "1", url = "https://jira.example.com"))

        service.createJiraTicket(1L, "PROJ", "Ticket", "Desc", "Story", emptyList(), historyId = null)

        verify(recordPort, org.mockito.kotlin.never()).findTicketCreationRecordById(any())
    }

    @Test
    fun `createJiraTicket skips history update when historyId record is not found`() {
        val config = jiraConfig()
        whenever(jiraConfigPort.findJiraConfigById(1L)).thenReturn(config)
        whenever(jiraPort.createTicket(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(CreatedJiraTicket(key = "PROJ-1", id = "1", url = "https://jira.example.com"))
        whenever(recordPort.findTicketCreationRecordById(77L)).thenReturn(null)

        val result = service.createJiraTicket(1L, "PROJ", "Ticket", "Desc", "Story", emptyList(), historyId = 77L)

        verify(recordPort).findTicketCreationRecordById(77L)
        verify(recordPort, org.mockito.kotlin.never()).saveTicketCreationRecord(any())
        assertEquals("PROJ-1", result["key"])
    }

    @Test
    fun `generateTicket defaults to Story when no issueType is supplied`() {
        val json = """{"summary":"Default","description":"D","issueType":"Story","labels":[]}"""
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(json)

        service.generateTicket("Create default ticket")

        org.mockito.kotlin.verify(promptReader).get(
            eq("en"),
            eq(IssueType.STORY.promptKey),
            any(),
        )
    }

    @Test
    fun `generateTicket passes null issueType to default to Story prompt key`() {
        val json = """{"summary":"Null","description":"D","issueType":"Story","labels":[]}"""
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(json)

        service.generateTicket("Create null ticket", issueType = null)

        org.mockito.kotlin.verify(promptReader).get(
            eq("en"),
            eq(IssueType.STORY.promptKey),
            any(),
        )
    }

    @Test
    fun `generateTicket loads the user-story prompt key for Story`() {
        val json = """{"summary":"Story","description":"D","issueType":"Story","labels":[]}"""
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(json)

        service.generateTicket("Create a story", issueType = IssueType.STORY)

        org.mockito.kotlin.verify(promptReader).get(
            eq("en"),
            eq("ticketcreation.create_story"),
            any(),
        )
    }

    @Test
    fun `generateTicket loads the technical-task prompt key for Technical Task`() {
        val json = """{"summary":"TT","description":"D","issueType":"Technical Task","labels":[]}"""
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(json)

        service.generateTicket("Refactor module", issueType = IssueType.TECHNICAL_TASK)

        org.mockito.kotlin.verify(promptReader).get(
            eq("en"),
            eq("ticketcreation.create_technical_task"),
            any(),
        )
    }

    @Test
    fun `generateTicket loads the bug prompt key for Bug`() {
        val json = """{"summary":"Bug","description":"D","issueType":"Bug","labels":[]}"""
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(json)

        service.generateTicket("Login broken", issueType = IssueType.BUG)

        org.mockito.kotlin.verify(promptReader).get(
            eq("en"),
            eq("ticketcreation.create_bug"),
            any(),
        )
    }

    @Test
    fun `generateTicket overrides AI's returned issueType with the user selection`() {
        val json = """{"summary":"T","description":"D","issueType":"Story","labels":[]}"""
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(json)

        val result = service.generateTicket("Create a bug", issueType = IssueType.BUG)

        assertEquals("Bug", result["issueType"])
    }

    @Test
    fun `generateTicket stores the user-selected issueType in the RUNNING record`() {
        val json = """{"summary":"T","description":"D","issueType":"Bug","labels":[]}"""
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(json)

        service.generateTicket("Crash on submit", issueType = IssueType.BUG)

        val inOrder = org.mockito.kotlin.inOrder(recordPort)
        inOrder.verify(recordPort).saveTicketCreationRecord(
            org.mockito.kotlin.check { rec ->
                assertEquals("RUNNING", rec.status)
                assertEquals("Bug", rec.issueType)
            },
        )
    }

    @Test
    fun `generateTicket stores the user-selected issueType in the COMPLETED record`() {
        val json = """{"summary":"Story","description":"D","issueType":"Story","labels":[]}"""
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(json)

        service.generateTicket("Create a story", issueType = IssueType.STORY)

        val inOrder = org.mockito.kotlin.inOrder(recordPort)
        inOrder.verify(recordPort).saveTicketCreationRecord(
            org.mockito.kotlin.check { rec ->
                assertEquals("COMPLETED", rec.status)
                assertEquals("Story", rec.issueType)
            },
        )
    }

    @Test
    fun `getAllRecords delegates to record port`() {
        val records =
            listOf(
                TicketCreationRecord(id = 1L, summary = "First"),
                TicketCreationRecord(id = 2L, summary = "Second"),
            )
        whenever(recordPort.findRecentTicketCreationRecords(200)).thenReturn(records)

        val result = service.getAllRecords()

        assertEquals(2, result.size)
        assertEquals("First", result[0].summary)
    }

    @Test
    fun `getAllRecords returns empty list when no records exist`() {
        whenever(recordPort.findAllTicketCreationRecordsByCreatedAtDesc()).thenReturn(emptyList())

        assertEquals(emptyList<TicketCreationRecord>(), service.getAllRecords())
    }

    @Test
    fun `getRecord returns record when found`() {
        val record = TicketCreationRecord(id = 3L, summary = "Found")
        whenever(recordPort.findTicketCreationRecordById(3L)).thenReturn(record)

        val result = service.getRecord(3L)

        assertNotNull(result)
        assertEquals(3L, result!!.id)
        assertEquals("Found", result.summary)
    }

    @Test
    fun `getRecord returns null when not found`() {
        whenever(recordPort.findTicketCreationRecordById(404L)).thenReturn(null)

        val result = service.getRecord(404L)

        org.junit.jupiter.api.Assertions
            .assertNull(result)
    }

    @Test
    fun `deleteRecord delegates to record port`() {
        service.deleteRecord(10L)

        verify(recordPort).deleteTicketCreationRecordById(10L)
    }

    @Test
    fun `deleteAllRecords delegates to record port`() {
        service.deleteAllRecords()

        verify(recordPort).deleteAllTicketCreationRecords()
    }

    @Test
    fun `generateTicket discards AI result when record was deleted during generation`() {
        val json = """{"summary":"T","description":"D","issueType":"Story","labels":[]}"""
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(json)
        whenever(recordPort.findTicketCreationRecordById(any())).thenReturn(null)

        val result = service.generateTicket("Create a ticket")

        assertEquals(emptyMap<String, Any?>(), result)
        verify(recordPort, org.mockito.kotlin.times(1)).saveTicketCreationRecord(any())
    }

    @Test
    fun `generateTicket discards AI result when record is CANCELLED during generation`() {
        val json = """{"summary":"T","description":"D","issueType":"Story","labels":[]}"""
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(json)
        whenever(recordPort.findTicketCreationRecordById(any()))
            .thenReturn(TicketCreationRecord(status = "CANCELLED"))

        val result = service.generateTicket("Create a ticket")

        assertEquals(emptyMap<String, Any?>(), result)
        verify(recordPort, org.mockito.kotlin.times(1)).saveTicketCreationRecord(any())
    }

    @Test
    fun `generateTicket skips FAILED update when record was deleted during exception`() {
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenThrow(RuntimeException("AI down"))
        whenever(recordPort.findTicketCreationRecordById(any())).thenReturn(null)

        assertThrows<RuntimeException> { service.generateTicket("input") }

        verify(recordPort, org.mockito.kotlin.times(1)).saveTicketCreationRecord(any())
    }

    @Test
    fun `generateTicket skips FAILED update when record is already CANCELLED during exception`() {
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenThrow(RuntimeException("AI down"))
        whenever(recordPort.findTicketCreationRecordById(any()))
            .thenReturn(TicketCreationRecord(status = "CANCELLED"))

        assertThrows<RuntimeException> { service.generateTicket("input") }

        verify(recordPort, org.mockito.kotlin.times(1)).saveTicketCreationRecord(any())
    }

    @Test
    fun `cancelRecord returns true and saves CANCELLED when record is RUNNING`() {
        val running = TicketCreationRecord(id = 10L, inputText = "input", status = "RUNNING")
        whenever(recordPort.findTicketCreationRecordById(10L)).thenReturn(running)

        val result = service.cancelRecord(10L)

        assertTrue(result)
        verify(recordPort).saveTicketCreationRecord(
            org.mockito.kotlin.check { rec ->
                assertEquals(10L, rec.id)
                assertEquals("CANCELLED", rec.status)
            },
        )
    }

    @Test
    fun `cancelRecord returns false when record is not found`() {
        whenever(recordPort.findTicketCreationRecordById(999L)).thenReturn(null)

        assertFalse(service.cancelRecord(999L))
        verify(recordPort, org.mockito.kotlin.never()).saveTicketCreationRecord(any())
    }

    @Test
    fun `cancelRecord returns false when record is already COMPLETED`() {
        whenever(recordPort.findTicketCreationRecordById(5L))
            .thenReturn(TicketCreationRecord(id = 5L, status = "COMPLETED"))

        assertFalse(service.cancelRecord(5L))
        verify(recordPort, org.mockito.kotlin.never()).saveTicketCreationRecord(any())
    }

    @Test
    fun `createJiraTicket uses demo mode when config name contains Demo`() {
        whenever(jiraConfigPort.findJiraConfigById(1L)).thenReturn(
            JiraConfig(id = 1L, name = "Demo Jira", baseUrl = "https://jira.example.com", apiToken = "token"),
        )

        val result = service.createJiraTicket(1L, "PROJ", "Summary", "Desc", "Bug", emptyList())

        assertEquals("DEMO", result["id"])
        assertTrue((result["key"] as String).startsWith("PROJ-"))
        verify(jiraPort, org.mockito.kotlin.never()).createTicket(any(), any(), any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `createJiraTicket demo mode saves user-selected issueType when historyId present`() {
        val existing = TicketCreationRecord(id = 20L, issueType = "Story", status = "COMPLETED")
        whenever(jiraConfigPort.findJiraConfigById(1L)).thenReturn(
            JiraConfig(id = 1L, name = "My Demo", baseUrl = "https://jira.example.com", apiToken = "token"),
        )
        whenever(recordPort.findTicketCreationRecordById(20L)).thenReturn(existing)

        service.createJiraTicket(1L, "PROJ", "Summary", "Desc", "Bug", emptyList(), historyId = 20L)

        verify(recordPort).saveTicketCreationRecord(
            org.mockito.kotlin.check { rec ->
                assertEquals("Bug", rec.issueType)
            },
        )
    }

    @Test
    fun `createJiraTicket demo mode with no historyId returns empty url`() {
        whenever(jiraConfigPort.findJiraConfigById(1L)).thenReturn(
            JiraConfig(id = 1L, name = "Demo", baseUrl = "https://jira.example.com", apiToken = "token"),
        )

        val result = service.createJiraTicket(1L, "PROJ", "Summary", "Desc", "Story", emptyList(), historyId = null)

        assertEquals("", result["url"])
        assertEquals("DEMO", result["id"])
    }

    @Test
    fun `createJiraTicket saves user-selected issueType not existing AI issueType`() {
        val existing = TicketCreationRecord(id = 7L, issueType = "Story", status = "COMPLETED")
        whenever(jiraConfigPort.findJiraConfigById(1L)).thenReturn(jiraConfig())
        whenever(jiraPort.createTicket(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(CreatedJiraTicket(key = "PROJ-1", id = "1", url = "https://jira.example.com"))
        whenever(recordPort.findTicketCreationRecordById(7L)).thenReturn(existing)

        service.createJiraTicket(1L, "PROJ", "Ticket", "Desc", "Bug", emptyList(), historyId = 7L)

        verify(recordPort).saveTicketCreationRecord(
            org.mockito.kotlin.check { rec ->
                assertEquals("Bug", rec.issueType)
            },
        )
    }

    @Test
    fun `saveFailedAttempt saves record with FAILED status and error message`() {
        service.saveFailedAttempt("Some input text", "Error occurred")

        verify(recordPort).saveTicketCreationRecord(
            org.mockito.kotlin.check { rec ->
                assertEquals("FAILED", rec.status)
                assertEquals("Some input text", rec.inputText)
                assertEquals("Error occurred", rec.errorMessage)
            },
        )
    }

    @Test
    fun `saveFailedAttempt truncates inputText to 2000 characters`() {
        val longInput = "x".repeat(3000)
        service.saveFailedAttempt(longInput, "Error")

        verify(recordPort).saveTicketCreationRecord(
            org.mockito.kotlin.check { rec ->
                assertEquals(2000, rec.inputText.length)
            },
        )
    }

    @Test
    fun `createJiraTicket creates each subtask under the new parent via the port`() {
        val config = jiraConfig(cloudMode = true)
        whenever(jiraConfigPort.findJiraConfigById(1L)).thenReturn(config)
        whenever(jiraPort.createTicket(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(CreatedJiraTicket(key = "PROJ-1", id = "10001", url = "https://jira/browse/PROJ-1"))
        whenever(jiraPort.createSubtask(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(CreatedJiraTicket(key = "PROJ-2", id = "10002", url = "https://jira/browse/PROJ-2"))

        val drafts =
            listOf(
                SubtaskDraft(summary = "First subtask", description = "Do X"),
                SubtaskDraft(summary = "Second subtask", description = "Do Y"),
            )
        val result = service.createJiraTicket(1L, "PROJ", "Parent", "Body", "Story", emptyList(), subtasks = drafts)

        verify(jiraPort, org.mockito.kotlin.times(2)).createSubtask(
            eq(config),
            eq("PROJ-1"),
            eq("PROJ"),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
        )
        @Suppress("UNCHECKED_CAST")
        val subtasks = result["subtasks"] as List<Map<String, Any?>>
        assertEquals(2, subtasks.size)
        assertEquals("First subtask", subtasks[0]["summary"])
        assertEquals("PROJ-2", subtasks[0]["key"])
    }

    @Test
    fun `createJiraTicket always forces subtask issueType to Subtask regardless of the draft`() {
        val config = jiraConfig(cloudMode = true)
        whenever(jiraConfigPort.findJiraConfigById(1L)).thenReturn(config)
        whenever(jiraPort.createTicket(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(CreatedJiraTicket(key = "PROJ-1", id = "10001", url = "https://jira/browse/PROJ-1"))
        whenever(jiraPort.createSubtask(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(CreatedJiraTicket(key = "PROJ-2", id = "10002", url = ""))

        val drafts =
            listOf(
                SubtaskDraft(summary = "Sub A", description = "Do A", issueType = "Story"),
                SubtaskDraft(summary = "Sub B", description = "Do B", issueType = "Story"),
                SubtaskDraft(summary = "Sub C", description = "Do C", issueType = "Task"),
                SubtaskDraft(summary = "Sub D", description = "Do D", issueType = ""),
            )
        service.createJiraTicket(1L, "PROJ", "Parent", "Body", "Story", emptyList(), subtasks = drafts)

        verify(jiraPort, org.mockito.kotlin.times(4)).createSubtask(
            org.mockito.kotlin.eq(config),
            org.mockito.kotlin.eq("PROJ-1"),
            org.mockito.kotlin.eq("PROJ"),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.eq("Subtask"),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
        )
    }

    @Test
    fun `createJiraTicket reports Subtask as the subtask issueType in the response`() {
        val config = jiraConfig(cloudMode = true)
        whenever(jiraConfigPort.findJiraConfigById(1L)).thenReturn(config)
        whenever(jiraPort.createTicket(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(CreatedJiraTicket(key = "PROJ-1", id = "10001", url = "https://jira/browse/PROJ-1"))
        whenever(jiraPort.createSubtask(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(CreatedJiraTicket(key = "PROJ-2", id = "10002", url = ""))

        val drafts = listOf(SubtaskDraft(summary = "Sub", description = "d", issueType = "Story"))
        val result = service.createJiraTicket(1L, "PROJ", "Parent", "Body", "Story", emptyList(), subtasks = drafts)

        @Suppress("UNCHECKED_CAST")
        val subs = result["subtasks"] as List<Map<String, Any?>>
        assertEquals(1, subs.size)
        assertEquals("Subtask", subs[0]["issueType"], "response must report Subtask so the UI shows the correct badge")
    }

    @Test
    fun `createJiraTicket returns empty subtasks list when no subtasks provided`() {
        val config = jiraConfig(cloudMode = true)
        whenever(jiraConfigPort.findJiraConfigById(1L)).thenReturn(config)
        whenever(jiraPort.createTicket(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(CreatedJiraTicket(key = "PROJ-1", id = "1", url = "https://jira/browse/PROJ-1"))

        val result = service.createJiraTicket(1L, "PROJ", "Parent", "Body", "Story", emptyList(), subtasks = emptyList())

        @Suppress("UNCHECKED_CAST")
        val subtasks = result["subtasks"] as List<Map<String, Any?>>
        assertEquals(0, subtasks.size)
        verify(jiraPort, org.mockito.kotlin.never()).createSubtask(any(), any(), any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `createJiraTicket does not call createSubtask when parent creation fails`() {
        val config = jiraConfig(cloudMode = true)
        whenever(jiraConfigPort.findJiraConfigById(1L)).thenReturn(config)
        whenever(jiraPort.createTicket(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenThrow(RuntimeException("parent failed"))

        assertThrows<RuntimeException> {
            service.createJiraTicket(
                1L,
                "PROJ",
                "Parent",
                "Body",
                "Story",
                emptyList(),
                subtasks = listOf(SubtaskDraft(summary = "st", description = "")),
            )
        }
        verify(jiraPort, org.mockito.kotlin.never()).createSubtask(any(), any(), any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `createJiraTicket propagates a typed failure when a subtask fails`() {
        val config = jiraConfig(cloudMode = true)
        whenever(jiraConfigPort.findJiraConfigById(1L)).thenReturn(config)
        whenever(jiraPort.createTicket(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(CreatedJiraTicket(key = "PROJ-1", id = "1", url = "https://jira/browse/PROJ-1"))
        whenever(jiraPort.createSubtask(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenThrow(RuntimeException("transient"))

        assertThrows<SubtaskCreationException> {
            service.createJiraTicket(
                1L,
                "PROJ",
                "Parent",
                "Body",
                "Story",
                emptyList(),
                subtasks =
                    listOf(
                        SubtaskDraft(summary = "Will fail", description = ""),
                        SubtaskDraft(summary = "Will succeed", description = ""),
                    ),
            )
        }

        verify(jiraPort, org.mockito.kotlin.times(1)).createSubtask(any(), any(), any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `createJiraTicket demo mode returns simulated subtask keys`() {
        val existing = TicketCreationRecord(id = 42L, status = "COMPLETED")
        whenever(jiraConfigPort.findJiraConfigById(1L)).thenReturn(
            JiraConfig(id = 1L, name = "Demo Jira", baseUrl = "https://jira.example.com", apiToken = "token"),
        )
        whenever(recordPort.findTicketCreationRecordById(42L)).thenReturn(existing)

        val result =
            service.createJiraTicket(
                1L,
                "PROJ",
                "Parent",
                "Body",
                "Story",
                emptyList(),
                historyId = 42L,
                subtasks =
                    listOf(
                        SubtaskDraft(summary = "Sub A", description = "Da"),
                        SubtaskDraft(summary = "Sub B", description = "Db"),
                    ),
            )

        @Suppress("UNCHECKED_CAST")
        val subtasks = result["subtasks"] as List<Map<String, Any?>>
        assertEquals(2, subtasks.size)
        assertEquals("Sub A", subtasks[0]["summary"])
        assertEquals("Sub B", subtasks[1]["summary"])
        assertEquals("DEMO", result["id"])
        verify(jiraPort, org.mockito.kotlin.never()).createTicket(any(), any(), any(), any(), any(), any(), any(), any())
        verify(jiraPort, org.mockito.kotlin.never()).createSubtask(any(), any(), any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `createJiraTicket demo mode without subtasks returns empty subtasks list`() {
        val existing = TicketCreationRecord(id = 11L, status = "COMPLETED")
        whenever(jiraConfigPort.findJiraConfigById(1L)).thenReturn(
            JiraConfig(id = 1L, name = "Demo Jira", baseUrl = "https://jira.example.com", apiToken = "token"),
        )
        whenever(recordPort.findTicketCreationRecordById(11L)).thenReturn(existing)

        val result =
            service.createJiraTicket(1L, "PROJ", "Parent", "Body", "Story", emptyList(), historyId = 11L, subtasks = emptyList())

        @Suppress("UNCHECKED_CAST")
        val subtasks = result["subtasks"] as List<Map<String, Any?>>
        assertEquals(0, subtasks.size)
        assertEquals("DEMO", result["id"])
    }

    @Test
    fun `createJiraTicket ensures subtask description ends with separator`() {
        val config = jiraConfig(cloudMode = false)
        whenever(jiraConfigPort.findJiraConfigById(1L)).thenReturn(config)
        whenever(jiraPort.createTicket(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(CreatedJiraTicket(key = "PROJ-1", id = "1", url = "https://jira/browse/PROJ-1"))
        whenever(jiraPort.createSubtask(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(CreatedJiraTicket(key = "PROJ-2", id = "2", url = "https://jira/browse/PROJ-2"))

        service.createJiraTicket(
            1L,
            "PROJ",
            "Parent",
            "Body",
            "Story",
            emptyList(),
            subtasks = listOf(SubtaskDraft(summary = "Sub", description = "Body without trailing sep")),
        )

        org.mockito.kotlin.verify(jiraPort).createSubtask(
            eq(config),
            eq("PROJ-1"),
            eq("PROJ"),
            eq("Sub"),
            org.mockito.kotlin.check { desc -> assertTrue(desc.trimEnd().endsWith("---")) },
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
        )
    }

    @Test
    fun `simulateDemoTicket default parameter synthetic method is reachable via reflection`() {
        whenever(recordPort.findTicketCreationRecordById(any())).thenReturn(null)
        val sim = DemoTicketSimulator(recordPort = recordPort, random = { 4242 })
        val result =
            sim.simulate(
                jiraConfigId = 1L,
                projectKey = "PROJ",
                issueType = "Story",
                labels = emptyList(),
                historyId = 42L,
                configName = "Demo Config",
                subtasks = emptyList(),
            )
        assertEquals("DEMO", result["id"])
    }

    @Test
    fun `simulateDemoTicket picks deterministic ticket number when historyId is provided`() {
        whenever(recordPort.findTicketCreationRecordById(12345L)).thenReturn(null)
        val sim = DemoTicketSimulator(recordPort = recordPort, random = { 4242 })
        val result =
            sim.simulate(
                jiraConfigId = 1L,
                projectKey = "PROJ",
                issueType = "Story",
                labels = emptyList(),
                historyId = 12345L,
                configName = "Demo",
                subtasks = emptyList(),
            )
        assertTrue((result["key"] as String).endsWith("-4345"))
        assertTrue((result["url"] as String).contains("/12345"))
    }

    @Test
    fun `generateTicket passes German output language hint when copilot lang is de`() {
        val json = """{"summary":"T","description":"D","issueType":"Story","labels":[]}"""
        whenever(copilot.resolveLanguage()).thenReturn("de")
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(json)

        service.generateTicket("Create a ticket")

        org.mockito.kotlin.verify(promptReader).get(
            org.mockito.kotlin.eq("de"),
            org.mockito.kotlin.eq(IssueType.STORY.promptKey),
            org.mockito.kotlin.check { vars -> assertEquals("German", vars["outputLanguage"]) },
        )
    }

    @Test
    fun `generateTicket default to English output language when copilot lang is en`() {
        val json = """{"summary":"T","description":"D","issueType":"Story","labels":[]}"""
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(json)

        service.generateTicket("Create a ticket")

        org.mockito.kotlin.verify(promptReader).get(
            org.mockito.kotlin.eq("en"),
            org.mockito.kotlin.eq(IssueType.STORY.promptKey),
            org.mockito.kotlin.check { vars -> assertEquals("English", vars["outputLanguage"]) },
        )
    }

    @Test
    fun `generateTicket with null labels in AI result produces null labels in record`() {
        val json = """{"summary":"T","description":"D","issueType":"Story"}"""
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(json)

        service.generateTicket("Create a ticket")

        val inOrder = org.mockito.kotlin.inOrder(recordPort)
        inOrder.verify(recordPort).saveTicketCreationRecord(
            org.mockito.kotlin.check { rec ->
                assertEquals("COMPLETED", rec.status)
                assertNull(rec.labels)
            },
        )
    }

    @Test
    fun `generateTicket with empty labels list produces empty string in record`() {
        val json = """{"summary":"T","description":"D","issueType":"Story","labels":[]}"""
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(json)

        service.generateTicket("Create a ticket")

        val inOrder = org.mockito.kotlin.inOrder(recordPort)
        inOrder.verify(recordPort).saveTicketCreationRecord(
            org.mockito.kotlin.check { rec ->
                assertEquals("COMPLETED", rec.status)
                assertEquals("", rec.labels)
            },
        )
    }

    @Test
    fun `generateTicket with multi-element labels list joins them with commas`() {
        val json = """{"summary":"T","description":"D","issueType":"Story","labels":["a","b","c"]}"""
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(json)

        service.generateTicket("Create a ticket")

        val inOrder = org.mockito.kotlin.inOrder(recordPort)
        inOrder.verify(recordPort).saveTicketCreationRecord(
            org.mockito.kotlin.check { rec ->
                assertEquals("COMPLETED", rec.status)
                assertEquals("a,b,c", rec.labels)
            },
        )
    }

    @Test
    fun `generateTicket captures FAILED status with error message on exception`() {
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenThrow(RuntimeException("boom"))
        whenever(recordPort.findTicketCreationRecordById(any())).thenReturn(TicketCreationRecord(status = "RUNNING"))

        assertThrows<RuntimeException> { service.generateTicket("input") }

        val inOrder = org.mockito.kotlin.inOrder(recordPort)
        inOrder.verify(recordPort).saveTicketCreationRecord(
            org.mockito.kotlin.check { rec ->
                assertEquals("FAILED", rec.status)
                assertEquals("boom", rec.errorMessage)
            },
        )
    }

    @Test
    fun `generateTicket swallows secondary exception when FAILED record update also throws`() {
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenThrow(RuntimeException("AI boom"))
        whenever(recordPort.findTicketCreationRecordById(any())).thenReturn(TicketCreationRecord(status = "RUNNING"))
        whenever(recordPort.saveTicketCreationRecord(any()))
            .thenAnswer { it.arguments[0] as TicketCreationRecord }
            .thenThrow(RuntimeException("disk full"))

        val ex = assertThrows<RuntimeException> { service.generateTicket("input") }
        assertEquals("AI boom", ex.message)
    }

    @Test
    fun `generateTicket findMatchingBrace throws when no JSON object exists in response`() {
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn("just plain text, no braces at all")
        val ex = assertThrows<IllegalArgumentException> { service.generateTicket("input") }
        assertTrue(ex.message!!.contains("No JSON object"))
    }

    @Test
    fun `createSubtasks uses default Minor priority when subtask draft priority is blank`() {
        val config = jiraConfig(cloudMode = true)
        whenever(jiraConfigPort.findJiraConfigById(1L)).thenReturn(config)
        whenever(jiraPort.createTicket(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(CreatedJiraTicket(key = "PROJ-1", id = "1", url = "u"))
        whenever(jiraPort.createSubtask(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(CreatedJiraTicket(key = "PROJ-2", id = "2", url = ""))

        service.createJiraTicket(
            1L,
            "PROJ",
            "Parent",
            "Body",
            "Story",
            emptyList(),
            subtasks = listOf(SubtaskDraft(summary = "Sub", description = "", priority = "")),
        )

        org.mockito.kotlin.verify(jiraPort).createSubtask(
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.eq("Minor"),
        )
    }

    @Test
    fun `createSubtasks keeps blank priority when subtask draft provides a non-blank one`() {
        val config = jiraConfig(cloudMode = true)
        whenever(jiraConfigPort.findJiraConfigById(1L)).thenReturn(config)
        whenever(jiraPort.createTicket(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(CreatedJiraTicket(key = "PROJ-1", id = "1", url = "u"))
        whenever(jiraPort.createSubtask(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(CreatedJiraTicket(key = "PROJ-2", id = "2", url = ""))

        service.createJiraTicket(
            1L,
            "PROJ",
            "Parent",
            "Body",
            "Story",
            emptyList(),
            subtasks = listOf(SubtaskDraft(summary = "Sub", description = "", priority = "Critical")),
        )

        org.mockito.kotlin.verify(jiraPort).createSubtask(
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.eq("Critical"),
        )
    }

    @Test
    fun `createSubtasks fills fallback summary when draft summary is blank`() {
        val config = jiraConfig(cloudMode = true)
        whenever(jiraConfigPort.findJiraConfigById(1L)).thenReturn(config)
        whenever(jiraPort.createTicket(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(CreatedJiraTicket(key = "PROJ-1", id = "1", url = "u"))
        whenever(jiraPort.createSubtask(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(CreatedJiraTicket(key = "PROJ-2", id = "2", url = ""))

        service.createJiraTicket(
            1L,
            "PROJ",
            "Parent",
            "Body",
            "Story",
            emptyList(),
            subtasks = listOf(SubtaskDraft(summary = "   ", description = "")),
        )

        org.mockito.kotlin.verify(jiraPort).createSubtask(
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.eq("Subtask 1"),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
        )
    }
}
