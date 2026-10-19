package com.stacklens.systemtest

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class JiraTicketSummarySystemTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `GET list returns a valid JSON array`() {
        mockMvc
            .get("/api/ticket-summary")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            }
    }

    @Test
    fun `GET unknown id returns 404`() {
        mockMvc
            .get("/api/ticket-summary/999999")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `GET status for unknown id returns 404`() {
        mockMvc
            .get("/api/ticket-summary/999999/status")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `POST cancel for unknown id returns 204 even when nothing is running`() {
        mockMvc
            .post("/api/ticket-summary/999999/cancel")
            .andExpect { status { isNoContent() } }
    }

    @Test
    fun `DELETE for unknown id returns 204`() {
        mockMvc
            .delete("/api/ticket-summary/999999")
            .andExpect { status { isNoContent() } }
    }

    @Test
    fun `DELETE all returns 204 and clears existing summaries`() {
        mockMvc
            .delete("/api/ticket-summary")
            .andExpect { status { isNoContent() } }

        mockMvc
            .get("/api/ticket-summary")
            .andExpect {
                status { isOk() }
                jsonPath("$") { isArray() }
            }
    }

    @Test
    fun `GET default template returns a non-empty template string`() {
        mockMvc
            .get("/api/ticket-summary/template/default")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.template") { exists() }
            }
    }

    @Test
    fun `POST start with empty ticketKey returns 400`() {
        mockMvc
            .post("/api/ticket-summary") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"ticketKey": ""}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }
}
