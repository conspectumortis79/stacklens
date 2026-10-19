package com.stacklens.systemtest

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class AiTokenUsageSystemTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `GET usage for unknown kind returns 200 with AiUsageDto-empty body`() {
        mockMvc
            .get("/api/ai-usage/not_a_real_kind/1")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.promptTokens") { value(0) }
                jsonPath("$.totalTokens") { value(0) }
            }
    }

    @Test
    fun `GET usage for valid kind with unknown id returns 200 with AiUsageDto-empty body`() {
        mockMvc
            .get("/api/ai-usage/jira_ticket_summary/99999999")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.promptTokens") { value(0) }
            }
    }

    @Test
    fun `GET usage for valid kind without id returns 400 (path mismatch, not a 200)`() {
        mockMvc
            .get("/api/ai-usage/jira_ticket_summary")
            .andExpect { status { is4xxClientError() } }
    }
}
