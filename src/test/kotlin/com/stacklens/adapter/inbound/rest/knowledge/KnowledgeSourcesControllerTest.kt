package com.stacklens.adapter.inbound.rest.knowledge

import com.stacklens.application.port.inbound.knowledge.KnowledgeSourceUseCase
import com.stacklens.domain.model.jira.JiraTicketKey
import com.stacklens.domain.model.knowledge.KnowledgeSource
import com.stacklens.domain.model.knowledge.KnowledgeSourceType
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class KnowledgeSourcesControllerTest {
    private val sourceUseCase: KnowledgeSourceUseCase = mock()
    private val controller = KnowledgeSourcesController(sourceUseCase)
    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(controller)
            .setControllerAdvice(KnowledgeExceptionAdvice())
            .build()

    private fun source(
        id: Long,
        name: String,
        type: KnowledgeSourceType = KnowledgeSourceType.MANUAL,
    ): KnowledgeSource = KnowledgeSource(id = id, knowledgeBaseId = 1L, name = name, type = type)

    @Test
    fun `listSources returns all sources when no base id is supplied`() {
        whenever(sourceUseCase.listSources(eq(null))).thenReturn(
            listOf(source(1L, "alpha"), source(2L, "beta")),
        )

        mockMvc
            .perform(get("/api/knowledge/sources"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].name").value("alpha"))
            .andExpect(jsonPath("$[1].name").value("beta"))
    }

    @Test
    fun `listSources forwards the knowledgeBaseId query param to the use case`() {
        whenever(sourceUseCase.listSources(eq(7L))).thenReturn(listOf(source(11L, "from-base-7")))

        mockMvc
            .perform(get("/api/knowledge/sources").param("knowledgeBaseId", "7"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].name").value("from-base-7"))
    }

    @Test
    fun `getSource returns the source or null when not found`() {
        whenever(sourceUseCase.getSource(42L)).thenReturn(source(42L, "needle"))

        mockMvc
            .perform(get("/api/knowledge/sources/42"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("needle"))
    }

    @Test
    fun `createManualSource returns the new source`() {
        whenever(
            sourceUseCase.createManualSource(
                knowledgeBaseId = 1L,
                name = "note",
                content = "hello world",
                externalReference = null,
            ),
        ).thenReturn(source(99L, "note"))

        mockMvc
            .perform(
                post("/api/knowledge/sources/manual")
                    .contentType("application/json")
                    .content(
                        """{"knowledgeBaseId":1,"name":"note","content":"hello world"}""",
                    ),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(99))
            .andExpect(jsonPath("$.name").value("note"))
    }

    @Test
    fun `createConfluenceSource forwards all fields to the use case`() {
        whenever(
            sourceUseCase.createConfluenceSource(
                knowledgeBaseId = 1L,
                confluenceConfigId = 5L,
                rootPageUrl = "https://confluence/x",
                includeChildren = true,
                maxDepth = 7,
            ),
        ).thenReturn(source(50L, "conf-root", KnowledgeSourceType.CONFLUENCE))

        mockMvc
            .perform(
                post("/api/knowledge/sources/confluence")
                    .contentType("application/json")
                    .content(
                        """{"knowledgeBaseId":1,"confluenceConfigId":5,"rootPageUrl":"https://confluence/x","includeChildren":true,"maxDepth":7}""",
                    ),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(50))
    }

    @Test
    fun `createJiraSource returns the new source`() {
        whenever(
            sourceUseCase.createJiraSource(
                knowledgeBaseId = 1L,
                jiraConfigId = 9L,
                rootTicketKey = JiraTicketKey("PROJ-1"),
                includeChildren = true,
                includeComments = true,
                maxDepth = 5,
                includeLinkedTickets = true,
                includeParent = false,
            ),
        ).thenReturn(source(60L, "jira-root", KnowledgeSourceType.JIRA))

        mockMvc
            .perform(
                post("/api/knowledge/sources/jira")
                    .contentType("application/json")
                    .content(
                        """{"knowledgeBaseId":1,"jiraConfigId":9,"rootTicketKey":"PROJ-1","includeChildren":true,"includeComments":true,"maxDepth":5}""",
                    ),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(60))
    }

    @Test
    fun `syncSource delegates to the use case`() {
        mockMvc
            .perform(post("/api/knowledge/sources/42/sync"))
            .andExpect(status().isOk)
        verify(sourceUseCase).syncSource(42L)
    }

    @Test
    fun `reindexSource delegates to the use case`() {
        mockMvc
            .perform(post("/api/knowledge/sources/42/reindex"))
            .andExpect(status().isOk)
        verify(sourceUseCase).reindexSource(42L)
    }

    @Test
    fun `deleteSource returns ok`() {
        mockMvc
            .perform(delete("/api/knowledge/sources/42"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ok").value(true))
        verify(sourceUseCase).deleteSource(42L)
    }

    @Test
    fun `file-local path validation rejects unsupported extensions`() {
        mockMvc
            .perform(
                post("/api/knowledge/sources/file-local")
                    .contentType("application/json")
                    .content("""{"knowledgeBaseId":1,"path":"/tmp/foo.exe"}"""),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").exists())
    }
}
