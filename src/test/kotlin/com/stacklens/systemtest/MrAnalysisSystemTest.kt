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
class MrAnalysisSystemTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `GET list returns valid JSON array`() {
        mockMvc
            .get("/api/mr-analysis")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            }
    }

    @Test
    fun `GET single analysis for unknown id returns 404`() {
        mockMvc
            .get("/api/mr-analysis/999999")
            .andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun `GET status for unknown analysis returns 404`() {
        mockMvc
            .get("/api/mr-analysis/999999/status")
            .andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun `DELETE single for unknown analysis returns 404`() {
        mockMvc
            .delete("/api/mr-analysis/999999")
            .andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun `DELETE history returns 200 with deleted true`() {
        mockMvc
            .delete("/api/mr-analysis/history")
            .andExpect {
                status { isOk() }
                jsonPath("$.deleted") { value(true) }
            }
    }

    @Test
    fun `POST start with blank mrUrl returns 400`() {
        mockMvc
            .post("/api/mr-analysis") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"mrUrl": ""}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { isNotEmpty() }
            }
    }

    @Test
    fun `POST cancel for unknown analysis returns FAILED status`() {
        mockMvc
            .post("/api/mr-analysis/999999/cancel")
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("FAILED") }
            }
    }

    @Test
    fun `POST suggestions for unknown analysis returns 404`() {
        mockMvc
            .post("/api/mr-analysis/999999/suggestions") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"filePath": "src/Foo.kt", "lineNumber": 1, "suggestedCode": "x"}"""
            }.andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun `POST suggestions for unknown analysis with empty body still returns 404`() {
        mockMvc
            .post("/api/mr-analysis/999999/suggestions") {
                contentType = MediaType.APPLICATION_JSON
                content = """{}"""
            }.andExpect {
                status { isNotFound() }
            }
    }
}
