package com.stacklens.adapter.inbound.rest.knowledge

import com.stacklens.application.port.inbound.knowledge.KnowledgeBaseUseCase
import com.stacklens.domain.model.knowledge.KnowledgeBase
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class KnowledgeBasesControllerTest {
    private val baseUseCase: KnowledgeBaseUseCase = mock()
    private val controller = KnowledgeBasesController(baseUseCase)
    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(controller)
            .setControllerAdvice(KnowledgeExceptionAdvice())
            .build()

    private fun base(
        id: Long,
        name: String,
    ): KnowledgeBase = KnowledgeBase(id = id, name = name, description = "")

    @Test
    fun `listBases returns the use-case list`() {
        whenever(baseUseCase.listBases()).thenReturn(listOf(base(1L, "alpha"), base(2L, "beta")))

        mockMvc
            .perform(get("/api/knowledge/bases"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].name").value("alpha"))
            .andExpect(jsonPath("$[1].name").value("beta"))
    }

    @Test
    fun `createBase posts the new base through the use case`() {
        whenever(
            baseUseCase.createBase(
                name = "new-base",
                description = "desc",
                includeInGlobalChat = true,
            ),
        ).thenReturn(base(7L, "new-base"))

        mockMvc
            .perform(
                post("/api/knowledge/bases")
                    .contentType("application/json")
                    .content("""{"name":"new-base","description":"desc","includeInGlobalChat":true}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(7))
            .andExpect(jsonPath("$.name").value("new-base"))
    }

    @Test
    fun `createBase defaults missing optional fields`() {
        whenever(baseUseCase.createBase(eq("only-name"), eq(""), eq(true))).thenReturn(
            base(8L, "only-name"),
        )

        mockMvc
            .perform(
                post("/api/knowledge/bases")
                    .contentType("application/json")
                    .content("""{"name":"only-name"}"""),
            ).andExpect(status().isOk)
    }

    @Test
    fun `updateBase forwards all body fields to the use case`() {
        whenever(
            baseUseCase.updateBase(
                id = 42L,
                name = "renamed",
                description = "new-desc",
                active = false,
                includeInGlobalChat = false,
            ),
        ).thenReturn(base(42L, "renamed"))

        mockMvc
            .perform(
                put("/api/knowledge/bases/42")
                    .contentType("application/json")
                    .content(
                        """{"name":"renamed","description":"new-desc","active":false,"includeInGlobalChat":false}""",
                    ),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("renamed"))
    }

    @Test
    fun `clearBase returns ok`() {
        mockMvc
            .perform(post("/api/knowledge/bases/5/clear"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ok").value(true))
        verify(baseUseCase).clearBase(5L)
    }

    @Test
    fun `deleteBase returns ok`() {
        mockMvc
            .perform(delete("/api/knowledge/bases/9"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ok").value(true))
        verify(baseUseCase).deleteBase(9L)
    }

    @Test
    fun `IllegalArgumentException from the use case is mapped to 400 by the advice`() {
        whenever(baseUseCase.createBase(any(), any(), any())).thenThrow(
            IllegalArgumentException("invalid base name"),
        )

        mockMvc
            .perform(
                post("/api/knowledge/bases")
                    .contentType("application/json")
                    .content("""{"name":"x"}"""),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("invalid base name"))
    }
}
