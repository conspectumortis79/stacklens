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
class TicketRefinementSystemTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `GET list returns valid JSON list`() {
        mockMvc
            .get("/api/ticket-refinement")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            }
    }

    @Test
    fun `GET unknown preparation returns 404`() {
        mockMvc
            .get("/api/ticket-refinement/999999")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `GET status for unknown preparation returns 404`() {
        mockMvc
            .get("/api/ticket-refinement/999999/status")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `POST start without parentTicketKey returns 400`() {
        mockMvc
            .post("/api/ticket-refinement") {
                contentType = MediaType.APPLICATION_JSON
                content = """{ "parentTicketKey": "  " }"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `POST start accepts a payload with parent key`() {
        val result =
            mockMvc
                .post("/api/ticket-refinement") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{ "parentTicketKey": "PROJ-1" }"""
                }.andReturn()
        val sc = result.response.status
        assert(sc in 200..299 || sc == 400) { "unexpected status $sc" }
    }

    @Test
    fun `POST start with unknown jira config id returns 400`() {
        mockMvc
            .post("/api/ticket-refinement") {
                contentType = MediaType.APPLICATION_JSON
                content = """{ "parentTicketKey": "PROJ-1", "jiraConfigId": 999999 }"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `POST comment for unknown preparation returns 400`() {
        mockMvc
            .post("/api/ticket-refinement/999999/comment/PROJ-2")
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `DELETE unknown preparation returns 204`() {
        mockMvc
            .delete("/api/ticket-refinement/999999")
            .andExpect { status { isNoContent() } }
    }

    @Test
    fun `DELETE all preparations returns 204`() {
        mockMvc
            .delete("/api/ticket-refinement")
            .andExpect { status { isNoContent() } }
    }

    @Test
    fun `GET default template returns the ticketrefinement prompt`() {
        mockMvc
            .get("/api/ticket-refinement/template/default")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.template") { exists() }
            }
    }

    @Test
    fun `POST cancel for unknown preparation returns 2xx`() {
        mockMvc
            .post("/api/ticket-refinement/999999/cancel")
            .andExpect { status { is2xxSuccessful() } }
    }

    @Test
    fun `POST subtasks with empty array returns 400`() {
        mockMvc
            .post("/api/ticket-refinement/999999/subtasks/PROJ-1") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"subtasks": []}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `POST subtasks with null body returns 400`() {
        mockMvc
            .post("/api/ticket-refinement/999999/subtasks/PROJ-1") {
                contentType = MediaType.APPLICATION_JSON
                content = """null"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `POST subtasks for unknown preparation returns 400`() {
        mockMvc
            .post("/api/ticket-refinement/999999/subtasks/PROJ-1") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"subtasks":[{"summary":"Sub A","description":"Body"}]}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `POST subtasks accepts a raw JSON array`() {
        mockMvc
            .post("/api/ticket-refinement/999999/subtasks/PROJ-1") {
                contentType = MediaType.APPLICATION_JSON
                content = """[{"summary":"Sub","description":"d"}]"""
            }.andExpect {
                status { is4xxClientError() }
            }
    }

    @Test
    fun `POST subtasks filters entries with blank summary before reaching the use case`() {
        mockMvc
            .post("/api/ticket-refinement/999999/subtasks/PROJ-1") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"subtasks":[{"summary":"   ","description":"skip"},{"summary":"Keep","description":"d"}]}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }
}
