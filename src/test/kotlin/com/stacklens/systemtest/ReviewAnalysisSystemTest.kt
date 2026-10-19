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
class ReviewAnalysisSystemTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `GET list returns empty list or valid JSON list`() {
        mockMvc
            .get("/api/review-analyse")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            }
    }

    @Test
    fun `GET status for unknown analysis returns 404`() {
        mockMvc
            .get("/api/review-analyse/999999/status")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `GET result for unknown analysis returns 404`() {
        mockMvc
            .get("/api/review-analyse/999999")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `DELETE for unknown analysis returns 404`() {
        mockMvc
            .delete("/api/review-analyse/999999")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `DELETE history deletes all analyses`() {
        mockMvc
            .delete("/api/review-analyse/history")
            .andExpect {
                status { isOk() }
                jsonPath("$.deleted") { value(true) }
            }
    }

    @Test
    fun `GET files-browse without parameters returns directory listing`() {
        mockMvc
            .get("/api/review-analyse/files/browse")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.currentPath") { exists() }
                jsonPath("$.items") { exists() }
            }
    }

    @Test
    fun `GET files-browse with invalid path returns 400`() {
        mockMvc
            .get("/api/review-analyse/files/browse") {
                param("path", "/this/path/does/not/exist/12345")
            }.andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `GET download html for unknown analysis returns 404`() {
        mockMvc
            .get("/api/review-analyse/999999/download/html")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `GET download md for unknown analysis returns 404`() {
        mockMvc
            .get("/api/review-analyse/999999/download/md")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `POST save-to-path for unknown analysis returns 404 or 400`() {
        val result =
            mockMvc
                .post("/api/review-analyse/999999/save-to-path") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"path": "/tmp"}"""
                }.andReturn()
        assert(result.response.status in 400..499) { "unexpected status ${result.response.status}" }
    }
}
