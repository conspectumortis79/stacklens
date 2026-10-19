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
class AgentPipelineSystemTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `GET agent-pipeline returns list of runs`() {
        mockMvc
            .get("/api/agent-pipeline")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$") { isArray() }
            }
    }

    @Test
    fun `GET agent-pipeline status for unknown id returns 404`() {
        mockMvc
            .get("/api/agent-pipeline/999999")
            .andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun `POST agent-pipeline cancel for unknown id returns 404`() {
        mockMvc
            .post("/api/agent-pipeline/999999/cancel") {
                contentType = MediaType.APPLICATION_JSON
                content = ""
            }.andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun `GET agent-pipeline check-repo-access for unknown id returns 404`() {
        mockMvc
            .get("/api/agent-pipeline/999999/check-repo-access")
            .andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun `POST start with blank rawText returns 400`() {
        mockMvc
            .post("/api/agent-pipeline") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"rawText": ""}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { isNotEmpty() }
            }
    }

    @Test
    fun `POST start with rawText exceeding 50_000 chars returns 400`() {
        val longText = "x".repeat(50_001)
        mockMvc
            .post("/api/agent-pipeline") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"rawText": "$longText"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { isNotEmpty() }
            }
    }

    @Test
    fun `POST start-from-jira with blank ticketKey returns 400`() {
        mockMvc
            .post("/api/agent-pipeline/from-jira") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"ticketKey": "", "jiraConfigId": 1}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { isNotEmpty() }
            }
    }

    @Test
    fun `DELETE agent-pipeline removes all runs`() {
        mockMvc
            .delete("/api/agent-pipeline")
            .andExpect {
                status { isOk() }
                jsonPath("$.deleted") { value(true) }
            }
    }

    @Test
    fun `GET write-files for unknown id returns 404`() {
        mockMvc
            .get("/api/agent-pipeline/999999/write-files")
            .andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun `POST write-files for unknown id returns 404`() {
        mockMvc
            .post("/api/agent-pipeline/999999/write-files")
            .andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun `GET mockup-file with missing path query returns 400`() {
        mockMvc
            .get("/api/agent-pipeline/mockup-file")
            .andExpect {
                status {
                    is4xxClientError()
                }
            }
    }

    @Test
    fun `GET mockup-file with non-existent path returns 400`() {
        mockMvc
            .get("/api/agent-pipeline/mockup-file") {
                param("path", "/this/path/does/not/exist/stacklens.png")
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { isNotEmpty() }
            }
    }
}
