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
import org.springframework.test.web.servlet.put

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class LocalRepoSystemTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `GET local repos returns empty list or valid JSON list`() {
        mockMvc
            .get("/api/local-repos")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            }
    }

    @Test
    fun `POST local repo with non-existent path returns 400`() {
        mockMvc
            .post("/api/local-repos") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "name": "Test Repo",
                      "path": "/this/path/does/not/exist/stacklens_test",
                      "active": true
                    }
                    """.trimIndent()
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `POST local repo with valid path registers the repo`() {
        val homePath = System.getProperty("user.home")
        mockMvc
            .post("/api/local-repos") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"name": "Home Directory Test", "path": "$homePath", "active": true}"""
            }.andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.name") { value("Home Directory Test") }
                jsonPath("$.path") { exists() }
            }
    }

    @Test
    fun `PUT local repo for unknown id with invalid path returns 400`() {
        mockMvc
            .put("/api/local-repos/999999") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "name": "Updated Repo",
                      "path": "/this/path/does/not/exist/stacklens_test_update",
                      "active": true
                    }
                    """.trimIndent()
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `DELETE local repo for unknown id returns 404`() {
        mockMvc
            .delete("/api/local-repos/999999")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `GET list-directory without path returns home directory contents`() {
        mockMvc
            .get("/api/local-repos/list-directory")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.current") { exists() }
                jsonPath("$.directories") { exists() }
            }
    }

    @Test
    fun `GET list-directory with non-existent path returns 400`() {
        mockMvc
            .get("/api/local-repos/list-directory?path=/this/does/not/exist/stacklens")
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `GET browse-directory returns path (empty when no JavaFX display available)`() {
        mockMvc
            .get("/api/local-repos/browse-directory")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.path") { exists() }
            }
    }

    @Test
    fun `GET gitlab configs returns list of active gitlab configurations`() {
        mockMvc
            .get("/api/local-repos/gitlab/configs")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            }
    }

    @Test
    fun `GET gitlab projects for unknown config id returns 404`() {
        mockMvc
            .get("/api/local-repos/gitlab/999999/projects")
            .andExpect { status { isNotFound() } }
    }
}
