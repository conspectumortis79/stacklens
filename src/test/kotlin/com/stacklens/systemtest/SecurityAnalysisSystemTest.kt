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
class SecurityAnalysisSystemTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun testGetAll() {
        mockMvc
            .get("/api/bug-analysis/all")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            }
    }

    @Test
    fun testGetStatusNotFound() {
        mockMvc
            .get("/api/bug-analysis/analysis/999999/status")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun testDeleteNotFound() {
        mockMvc
            .delete("/api/bug-analysis/analysis/999999")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun testCancelReturnsStatusFailed() {
        mockMvc
            .post("/api/bug-analysis/analysis/999999/cancel")
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("FAILED") }
            }
    }

    @Test
    fun testStartLocalEndpointExists() {
        val result =
            mockMvc
                .post("/api/bug-analysis/start-local") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"repoConfigId": 999999, "iterations": 1}"""
                }.andReturn()
        assert(result.response.status != 404) { "Endpoint /api/bug-analysis/start-local must not return 404" }
    }

    @Test
    fun testStartGitlabEndpointExists() {
        val result =
            mockMvc
                .post("/api/bug-analysis/start-gitlab") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"gitlabConfigId": 999999, "projectId": 999999}"""
                }.andReturn()
        assert(result.response.status != 404) { "Endpoint /api/bug-analysis/start-gitlab must not return 404" }
    }

    @Test
    fun testStartGithubEndpointExists() {
        val result =
            mockMvc
                .post("/api/bug-analysis/start-github") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"githubRepoConfigId": 999999}"""
                }.andReturn()
        assert(result.response.status != 404) { "Endpoint /api/bug-analysis/start-github must not return 404" }
    }
}
