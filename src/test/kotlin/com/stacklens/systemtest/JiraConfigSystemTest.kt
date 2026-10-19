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
class JiraConfigSystemTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `GET jira-configs returns empty list or valid JSON list`() {
        mockMvc
            .get("/api/config/jira")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            }
    }

    @Test
    fun `POST jira-config creates new configuration`() {
        mockMvc
            .post("/api/config/jira") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "name": "Test Jira",
                      "baseUrl": "https://jira.example.com",
                      "projectKey": "TEST",
                      "username": "testuser",
                      "apiToken": "test-token",
                      "cloudMode": true,
                      "active": true
                    }
                    """.trimIndent()
            }.andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.name") { value("Test Jira") }
            }
    }

    @Test
    fun `DELETE jira-config for unknown ID returns 404`() {
        mockMvc
            .delete("/api/config/jira/999999")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `POST test-connection for unknown ID returns 404`() {
        mockMvc
            .post("/api/config/jira/999999/test")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `POST jira-config persists commentVisibilityRole and exposes it via GET`() {
        val unique = "VisRoleTest-${System.currentTimeMillis()}"
        mockMvc
            .post("/api/config/jira") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "name": "$unique",
                      "baseUrl": "https://jira.example.com",
                      "projectKey": "VRT",
                      "username": "u",
                      "apiToken": "t",
                      "cloudMode": true,
                      "commentVisibilityRole": "Developers",
                      "active": true
                    }
                    """.trimIndent()
            }.andExpect {
                status { isOk() }
                jsonPath("$.commentVisibilityRole") { value("Developers") }
            }

        mockMvc
            .get("/api/config/jira")
            .andExpect {
                status { isOk() }
                jsonPath("$[?(@.name == '$unique')].commentVisibilityRole") {
                    value("Developers")
                }
            }
    }

    @Test
    fun `POST jira-config trims commentVisibilityRole whitespace`() {
        val unique = "VisRoleTrim-${System.currentTimeMillis()}"
        mockMvc
            .post("/api/config/jira") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "name": "$unique",
                      "baseUrl": "https://jira.example.com",
                      "username": "u",
                      "apiToken": "t",
                      "commentVisibilityRole": "  Team Leads  "
                    }
                    """.trimIndent()
            }.andExpect {
                status { isOk() }
                jsonPath("$.commentVisibilityRole") { value("Team Leads") }
            }
    }

    @Test
    fun `POST jira-config without commentVisibilityRole defaults to empty string`() {
        val unique = "VisRoleDefault-${System.currentTimeMillis()}"
        mockMvc
            .post("/api/config/jira") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "name": "$unique",
                      "baseUrl": "https://jira.example.com",
                      "username": "u",
                      "apiToken": "t"
                    }
                    """.trimIndent()
            }.andExpect {
                status { isOk() }
                jsonPath("$.commentVisibilityRole") { value("") }
            }
    }
}
