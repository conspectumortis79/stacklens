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
class PipelineAnalysisSystemTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `GET all returns a JSON list (possibly empty)`() {
        mockMvc
            .get("/api/pipeline-analysis/all")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            }
    }

    @Test
    fun `GET status for unknown analysis returns 404`() {
        mockMvc
            .get("/api/pipeline-analysis/analysis/999999/status")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `DELETE for unknown analysis returns 404`() {
        mockMvc
            .delete("/api/pipeline-analysis/analysis/999999")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `GET latest local for unknown repo config returns OK JSON`() {
        mockMvc
            .get("/api/pipeline-analysis/local/2147483640/latest")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            }
    }

    @Test
    fun `GET latest gitlab for unknown config returns OK JSON`() {
        mockMvc
            .get("/api/pipeline-analysis/gitlab/2147483640/project/1/latest")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            }
    }

    @Test
    fun `GET latest github for unknown config returns OK JSON`() {
        mockMvc
            .get("/api/pipeline-analysis/github/2147483640/latest")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            }
    }

    @Test
    fun `POST start-local with valid JSON does not 404`() {
        val result =
            mockMvc
                .post("/api/pipeline-analysis/start-local") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"repoConfigId": 999999, "userPrompt": "focus on security"}"""
                }.andReturn()
        assertTrue(result.response.status != 404, "Endpoint /api/pipeline-analysis/start-local must not return 404")
    }

    @Test
    fun `POST start-gitlab with valid JSON does not 404`() {
        val result =
            mockMvc
                .post("/api/pipeline-analysis/start-gitlab") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"gitlabConfigId": 999999, "projectId": 999999, "projectPath": "g/p", "userPrompt": null}"""
                }.andReturn()
        assertTrue(result.response.status != 404, "Endpoint /api/pipeline-analysis/start-gitlab must not return 404")
    }

    @Test
    fun `POST start-github with valid JSON does not 404`() {
        val result =
            mockMvc
                .post("/api/pipeline-analysis/start-github") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"githubRepoConfigId": 999999, "userPrompt": "  "}"""
                }.andReturn()
        assertTrue(result.response.status != 404, "Endpoint /api/pipeline-analysis/start-github must not return 404")
    }

    @Test
    fun `POST cancel-latest-for-repo returns OK with cancelledId field`() {
        mockMvc
            .post("/api/pipeline-analysis/cancel-latest-for-repo/999999")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            }
    }

    @Test
    fun `POST cancel for unknown analysis returns status FAILED`() {
        mockMvc
            .post("/api/pipeline-analysis/analysis/999999/cancel")
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("FAILED") }
            }
    }

    @Test
    fun `POST create-mr-or-pr for unknown analysis returns 404`() {
        mockMvc
            .post("/api/pipeline-analysis/analysis/999999/create-mr-or-pr") {
                contentType = MediaType.APPLICATION_JSON
                content = """{}"""
            }.andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun `POST create-mr-or-pr accepts an empty JSON body`() {
        mockMvc
            .post("/api/pipeline-analysis/analysis/999999/create-mr-or-pr") {
                contentType = MediaType.APPLICATION_JSON
                content = """{}"""
            }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `POST create-mr-or-pr without a body returns 404 (not 400)`() {
        mockMvc
            .post("/api/pipeline-analysis/analysis/999999/create-mr-or-pr") {
                contentType = MediaType.APPLICATION_JSON
            }.andExpect { status { isNotFound() } }
    }
}
