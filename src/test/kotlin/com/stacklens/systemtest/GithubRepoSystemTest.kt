package com.stacklens.systemtest

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class GithubRepoSystemTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `GET github repos returns empty list or valid JSON list`() {
        mockMvc
            .get("/api/github-repos")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            }
    }

    @Test
    fun `POST github repo creates new configuration`() {
        mockMvc
            .post("/api/github-repos") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "name": "My Project",
                      "owner": "acme-corp",
                      "repo": "my-project",
                      "branch": "main",
                      "active": true
                    }
                    """.trimIndent()
            }.andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.name") { value("My Project") }
                jsonPath("$.owner") { value("acme-corp") }
                jsonPath("$.repo") { value("my-project") }
                jsonPath("$.id") { exists() }
            }
    }

    @Test
    fun `POST github repo with blank name returns 400`() {
        mockMvc
            .post("/api/github-repos") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"name": "   ", "owner": "acme", "repo": "project", "active": true}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `POST github repo with blank owner returns 400`() {
        mockMvc
            .post("/api/github-repos") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"name": "My Repo", "owner": "", "repo": "project", "active": true}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `POST github repo with blank repo name returns 400`() {
        mockMvc
            .post("/api/github-repos") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"name": "My Repo", "owner": "acme", "repo": "   ", "active": true}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `PUT github repo updates existing configuration`() {
        val createResult =
            mockMvc
                .post("/api/github-repos") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"name": "Temp Repo", "owner": "acme", "repo": "temp", "active": true}"""
                }.andReturn()

        val id =
            ObjectMapper()
                .readTree(createResult.response.contentAsString)
                .path("id")
                .asLong()

        mockMvc
            .put("/api/github-repos/$id") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "name": "Updated Repo",
                      "owner": "acme",
                      "repo": "temp",
                      "branch": "develop",
                      "active": false
                    }
                    """.trimIndent()
            }.andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.name") { value("Updated Repo") }
            }
    }

    @Test
    fun `PUT github repo for unknown id returns 404`() {
        mockMvc
            .put("/api/github-repos/999999") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"name": "Repo", "owner": "acme", "repo": "project", "active": true}"""
            }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `DELETE github repo removes configuration`() {
        val createResult =
            mockMvc
                .post("/api/github-repos") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"name": "To Delete", "owner": "acme", "repo": "delete-me", "active": true}"""
                }.andReturn()

        val id =
            ObjectMapper()
                .readTree(createResult.response.contentAsString)
                .path("id")
                .asLong()

        mockMvc
            .delete("/api/github-repos/$id")
            .andExpect { status { isNoContent() } }

        mockMvc
            .post("/api/github-repos/$id/test")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `DELETE github repo for unknown id returns 404`() {
        mockMvc
            .delete("/api/github-repos/999999")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `POST test connection for unknown id returns 404`() {
        mockMvc
            .post("/api/github-repos/999999/test")
            .andExpect { status { isNotFound() } }
    }
}
