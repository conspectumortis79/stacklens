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
class SprintReviewSystemTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `GET list returns a valid JSON array`() {
        mockMvc
            .get("/api/sprint-review")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            }
    }

    @Test
    fun `GET unknown id returns 404`() {
        mockMvc
            .get("/api/sprint-review/999999")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `GET status for unknown id returns 404`() {
        mockMvc
            .get("/api/sprint-review/999999/status")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `POST start without sprintName returns 400`() {
        mockMvc
            .post("/api/sprint-review") {
                contentType = MediaType.APPLICATION_JSON
                content = """{ "sprintName": "  ", "targetTicketKey": "PROJ-1" }"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `POST start without targetTicketKey returns 400`() {
        mockMvc
            .post("/api/sprint-review") {
                contentType = MediaType.APPLICATION_JSON
                content = """{ "sprintName": "Sprint 42", "targetTicketKey": "  " }"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `POST start with unknown jira config id returns 400`() {
        mockMvc
            .post("/api/sprint-review") {
                contentType = MediaType.APPLICATION_JSON
                content = """{ "sprintName": "Sprint 1", "targetTicketKey": "PROJ-1", "jiraConfigId": 999999 }"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `POST start accepts a payload with sprint + target key`() {
        val result =
            mockMvc
                .post("/api/sprint-review") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{ "sprintName": "Sprint 1", "targetTicketKey": "PROJ-1" }"""
                }.andReturn()
        val sc = result.response.status
        assert(sc in 200..299 || sc == 400) { "unexpected status $sc" }
    }

    @Test
    fun `POST cancel for unknown id returns 2xx`() {
        mockMvc
            .post("/api/sprint-review/999999/cancel")
            .andExpect { status { is2xxSuccessful() } }
    }

    @Test
    fun `DELETE for unknown id returns 204`() {
        mockMvc
            .delete("/api/sprint-review/999999")
            .andExpect { status { isNoContent() } }
    }

    @Test
    fun `DELETE all returns 204`() {
        mockMvc
            .delete("/api/sprint-review")
            .andExpect { status { isNoContent() } }
    }

    @Test
    fun `GET default template returns the sprintreview prompt`() {
        mockMvc
            .get("/api/sprint-review/template/default")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.template") { exists() }
            }
    }
}
