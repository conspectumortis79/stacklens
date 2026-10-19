package com.stacklens.systemtest

import org.junit.jupiter.api.Assertions.assertTrue
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
class CodeAnalysisSystemTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `GET all returns empty list or valid JSON list`() {
        mockMvc
            .get("/api/code-analysis/all")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            }
    }

    @Test
    fun `GET status for unknown analysis returns 404`() {
        mockMvc
            .get("/api/code-analysis/analysis/999999/status")
            .andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun `DELETE for unknown analysis returns 404`() {
        mockMvc
            .delete("/api/code-analysis/analysis/999999")
            .andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun `GET latest local for unknown repo config returns status NONE`() {
        mockMvc
            .get("/api/code-analysis/local/999999/latest")
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("NONE") }
            }
    }

    @Test
    fun `GET latest gitlab for unknown config returns status NONE`() {
        mockMvc
            .get("/api/code-analysis/gitlab/999999/project/1/latest")
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("NONE") }
            }
    }

    @Test
    fun `GET latest github for unknown config returns status NONE`() {
        mockMvc
            .get("/api/code-analysis/github/999999/latest")
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("NONE") }
            }
    }

    @Test
    fun `POST start-local with valid JSON starts analysis or returns error`() {
        mockMvc
            .post("/api/code-analysis/start-local") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"repoConfigId": 888888}"""
            }.andExpect {
            }
    }

    @Test
    fun `POST start-gitlab with valid JSON does not 404`() {
        val result =
            mockMvc
                .post("/api/code-analysis/start-gitlab") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"gitlabConfigId": 999999, "projectId": 999999}"""
                }.andReturn()
        assertTrue(result.response.status != 404, "Endpoint /api/code-analysis/start-gitlab must not return 404")
    }

    @Test
    fun `POST start-github with valid JSON does not 404`() {
        val result =
            mockMvc
                .post("/api/code-analysis/start-github") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"githubRepoConfigId": 999999}"""
                }.andReturn()
        assertTrue(result.response.status != 404, "Endpoint /api/code-analysis/start-github must not return 404")
    }

    @Test
    fun `POST save-diagram for unknown analysis responds without 5xx`() {
        val result =
            mockMvc
                .post("/api/code-analysis/analysis/999999/save-diagram") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"path": "/tmp", "filename": "x.drawio", "content": "<mxfile/>"}"""
                }.andReturn()
        assertTrue(result.response.status < 500, "Endpoint /api/code-analysis/analysis/{id}/save-diagram returned 5xx: ${result.response.status}")
    }

    @Test
    fun `POST cancel-latest-for-repo returns response`() {
        mockMvc
            .post("/api/code-analysis/cancel-latest-for-repo/999999")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            }
    }

    @Test
    fun `POST create-mr for unknown analysis returns 404`() {
        mockMvc
            .post("/api/code-analysis/analysis/999999/create-mr") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"gitlabConfigId": 1, "projectId": 1}"""
            }.andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun `POST cancel for unknown analysis returns status FAILED`() {
        mockMvc
            .post("/api/code-analysis/analysis/999999/cancel")
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("FAILED") }
            }
    }
}
