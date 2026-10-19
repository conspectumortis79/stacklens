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
class SearchSystemTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `GET sessions returns empty list or valid JSON list`() {
        mockMvc
            .get("/api/sessions")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            }
    }

    @Test
    fun `GET session for unknown ID returns 404`() {
        mockMvc
            .get("/api/sessions/999999")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `DELETE session for unknown ID returns 404`() {
        mockMvc
            .delete("/api/sessions/999999")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `DELETE all sessions removes all sessions`() {
        mockMvc
            .delete("/api/sessions")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.deleted") { exists() }
            }
    }

    @Test
    fun `GET session status for unknown ID returns 404`() {
        mockMvc
            .get("/api/search/999999/status")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `GET session results for unknown ID returns empty list`() {
        mockMvc
            .get("/api/sessions/999999/results")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            }
    }

    @Test
    fun `POST search starts new search session`() {
        mockMvc
            .post("/api/search") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "keywords": ["test"],
                      "gitlabRepositories": [],
                      "jiraProjects": [],
                      "jiraComponents": [],
                      "jiraLabels": [],
                      "confluenceWebsites": [],
                      "grafanaPages": [],
                      "pipelineFilters": [],
                      "includeGitlab": false,
                      "includeDatabase": false,
                      "includePipeline": false,
                      "includeConfluence": false,
                      "includeGrafana": false,
                      "includeJira": false,
                      "includeContextMap": false,
                      "crossReferenceEnabled": false,
                      "gitlabConfigIds": [],
                      "databaseConfigIds": [],
                      "confluenceConfigIds": [],
                      "grafanaConfigIds": [],
                      "jiraConfigIds": []
                    }
                    """.trimIndent()
            }.andExpect {
                status { isOk() }
                jsonPath("$.sessionId") { exists() }
                jsonPath("$.status") { exists() }
            }
    }

    @Test
    fun `POST cancel for unknown session returns 404`() {
        mockMvc
            .post("/api/sessions/999999/cancel")
            .andExpect { status { isNotFound() } }
    }
}
