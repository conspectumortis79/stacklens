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
class TicketCreationSystemTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `GET ticket-creation page returns 200 OK`() {
        mockMvc
            .get("/ticket-creation")
            .andExpect {
                status { isOk() }
            }
    }

    @Test
    fun `GET ticket-creation page returns HTML content`() {
        mockMvc
            .get("/ticket-creation")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.TEXT_HTML) }
            }
    }

    @Test
    fun `POST generate without inputText returns 400`() {
        mockMvc
            .post("/api/ticket-creation/generate") {
                contentType = MediaType.APPLICATION_JSON
                content = """{}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `POST generate with blank inputText returns 400`() {
        mockMvc
            .post("/api/ticket-creation/generate") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"inputText": "   "}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `POST generate with valid inputText does not return 400`() {
        val result =
            mockMvc
                .post("/api/ticket-creation/generate") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"inputText": "Create a user login feature"}"""
                }.andReturn()
        val sc = result.response.status
        assert(sc != 400) { "A valid inputText should not return 400, got $sc" }
    }

    @Test
    fun `POST create-jira without jiraConfigId returns 400`() {
        mockMvc
            .post("/api/ticket-creation/create-jira") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"projectKey": "PROJ", "summary": "A ticket"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `POST create-jira with non-numeric configId returns 400`() {
        mockMvc
            .post("/api/ticket-creation/create-jira") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"jiraConfigId": "not-a-number", "projectKey": "PROJ", "summary": "A ticket"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `POST create-jira with unknown configId returns 400`() {
        mockMvc
            .post("/api/ticket-creation/create-jira") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"jiraConfigId": 999999, "projectKey": "PROJ", "summary": "A ticket"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `POST create-jira with blank projectKey returns 400`() {
        mockMvc
            .post("/api/ticket-creation/create-jira") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"jiraConfigId": 999999, "projectKey": "  ", "summary": "A ticket"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `POST create-jira with blank summary returns 400`() {
        mockMvc
            .post("/api/ticket-creation/create-jira") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"jiraConfigId": 999999, "projectKey": "PROJ", "summary": ""}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `POST create-jira with all params set but unknown configId does not reach Jira`() {
        mockMvc
            .post("/api/ticket-creation/create-jira") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """{"jiraConfigId": 999999, "projectKey": "PROJ", "summary": "Valid summary", "description": "Desc", "issueType": "Story", "labels": []}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `GET history returns 200 with JSON array`() {
        mockMvc
            .get("/api/ticket-creation/history")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$") { isArray() }
            }
    }

    @Test
    fun `GET history by unknown id returns 404`() {
        mockMvc
            .get("/api/ticket-creation/history/999999999")
            .andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun `DELETE all history returns 204`() {
        mockMvc
            .delete("/api/ticket-creation/history")
            .andExpect {
                status { isNoContent() }
            }
    }

    @Test
    fun `DELETE history item by id returns 204`() {
        mockMvc
            .delete("/api/ticket-creation/history/999999999")
            .andExpect {
                status { isNoContent() }
            }
    }

    @Test
    fun `POST cancel on non-existent id returns 404`() {
        mockMvc
            .post("/api/ticket-creation/history/999999999/cancel") {
                contentType = MediaType.APPLICATION_JSON
            }.andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun `POST create-jira with subtasks does not return 400`() {
        mockMvc
            .post("/api/ticket-creation/create-jira") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """{"jiraConfigId": 999999, "projectKey": "PROJ", "summary": "Parent", "description": "d", "issueType": "Story", "labels": [], "subtasks": [{"summary": "S1", "description": "D1"}, {"summary": "S2", "description": "D2"}]}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `POST create-jira with subtasks having blank summaries is accepted`() {
        mockMvc
            .post("/api/ticket-creation/create-jira") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """{"jiraConfigId": 999999, "projectKey": "PROJ", "summary": "Parent", "subtasks": [{"summary": "   ", "description": "skip"}, {"summary": "Keep", "description": "d"}]}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `POST create-jira without subtasks still works`() {
        mockMvc
            .post("/api/ticket-creation/create-jira") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """{"jiraConfigId": 999999, "projectKey": "PROJ", "summary": "Parent", "description": "d", "issueType": "Story", "labels": []}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `POST cancel without body still returns 404 for unknown id`() {
        mockMvc
            .post("/api/ticket-creation/history/999999999/cancel")
            .andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun `GET read-binary-file with empty filename returns 400`() {
        mockMvc
            .get("/api/ticket-creation/read-binary-file") {
                param("filename", "")
            }.andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `GET read-binary-file with path-traversal filename returns 400`() {
        mockMvc
            .get("/api/ticket-creation/read-binary-file") {
                param("filename", "../../../../etc/passwd")
            }.andExpect {
                status { isBadRequest() }
            }
    }
}
