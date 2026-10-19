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
class LoadTestAnalysisSystemTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `GET all returns empty list or valid JSON list`() {
        mockMvc
            .get("/api/loadtest-analysis/all")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            }
    }

    @Test
    fun `GET status for unknown analysis returns 404`() {
        mockMvc
            .get("/api/loadtest-analysis/analysis/999999/status")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `DELETE for unknown analysis returns 404`() {
        mockMvc
            .delete("/api/loadtest-analysis/analysis/999999")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `POST cancel for unknown analysis returns status FAILED`() {
        mockMvc
            .post("/api/loadtest-analysis/analysis/999999/cancel")
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("FAILED") }
            }
    }

    @Test
    fun `GET latest gitlab for unknown config returns status NONE`() {
        mockMvc
            .get("/api/loadtest-analysis/gitlab/999999/project/1/latest")
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("NONE") }
            }
    }

    @Test
    fun `GET latest local for unused repo config returns status NONE`() {
        mockMvc
            .get("/api/loadtest-analysis/local/777777/latest")
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("NONE") }
            }
    }

    @Test
    fun `GET latest github for unknown config returns status NONE`() {
        mockMvc
            .get("/api/loadtest-analysis/github/999999/latest")
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("NONE") }
            }
    }

    @Test
    fun `GET k6-status for unknown analysis returns response`() {
        mockMvc
            .get("/api/loadtest-analysis/analysis/999999/k6-status")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            }
    }

    @Test
    fun `POST stop-k6 for unknown analysis returns stopped true`() {
        mockMvc
            .post("/api/loadtest-analysis/analysis/999999/stop-k6")
            .andExpect {
                status { isOk() }
                jsonPath("$.stopped") { value(true) }
            }
    }

    @Test
    fun `POST start-local does not 404`() {
        val result =
            mockMvc
                .post("/api/loadtest-analysis/start-local") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"repoConfigId": 999999}"""
                }.andReturn()
        assert(result.response.status != 404) { "Endpoint /api/loadtest-analysis/start-local must not return 404" }
    }

    @Test
    fun `POST start-gitlab does not 404`() {
        val result =
            mockMvc
                .post("/api/loadtest-analysis/start-gitlab") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"gitlabConfigId": 999999, "projectId": 999999}"""
                }.andReturn()
        assert(result.response.status != 404) { "Endpoint /api/loadtest-analysis/start-gitlab must not return 404" }
    }

    @Test
    fun `POST start-github does not 404`() {
        val result =
            mockMvc
                .post("/api/loadtest-analysis/start-github") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"githubRepoConfigId": 999999}"""
                }.andReturn()
        assert(result.response.status != 404) { "Endpoint /api/loadtest-analysis/start-github must not return 404" }
    }

    @Test
    fun `POST run-k6 for unknown analysis does not 404`() {
        val result =
            mockMvc
                .post("/api/loadtest-analysis/analysis/999999/run-k6") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"script": "export const options = {vus: 1, duration: '1s'};\nexport default function() {}"}"""
                }.andReturn()
        assert(result.response.status != 404) { "Endpoint /api/loadtest-analysis/analysis/{id}/run-k6 must not return 404" }
    }
}
