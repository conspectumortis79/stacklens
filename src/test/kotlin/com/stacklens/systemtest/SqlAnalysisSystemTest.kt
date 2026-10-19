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
class SqlAnalysisSystemTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `GET history returns empty list or valid JSON list`() {
        mockMvc
            .get("/api/sql-analyse/history")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            }
    }

    @Test
    fun `GET history for unknown ID returns 404`() {
        mockMvc
            .get("/api/sql-analyse/history/999999")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `DELETE history for unknown ID returns 404`() {
        mockMvc
            .delete("/api/sql-analyse/history/999999")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `DELETE history removes all entries`() {
        mockMvc
            .delete("/api/sql-analyse/history")
            .andExpect {
                status { isOk() }
                jsonPath("$.deleted") { value(true) }
            }
    }

    @Test
    fun `POST analyze with empty SQL returns 400`() {
        mockMvc
            .post("/api/sql-analyse/analyze") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"sql": ""}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `POST generate-data with empty SQL returns 400`() {
        mockMvc
            .post("/api/sql-analyse/generate-data") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"sql": ""}"""
            }.andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `POST generate-query with empty fields returns 400`() {
        mockMvc
            .post("/api/sql-analyse/generate-query") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"ddl": "", "goal": ""}"""
            }.andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `POST analyze-combined with empty SQL returns 400`() {
        mockMvc
            .post("/api/sql-analyse/analyze-combined") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"sql": ""}"""
            }.andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `POST run-test with empty SQL returns 400`() {
        mockMvc
            .post("/api/sql-analyse/run-test") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"sql": "", "testData": ""}"""
            }.andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `POST history with empty SQL returns 400`() {
        mockMvc
            .post("/api/sql-analyse/history") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"sql": ""}"""
            }.andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `POST history with valid SQL creates entry`() {
        mockMvc
            .post("/api/sql-analyse/history") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"sql": "SELECT 1"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.id") { exists() }
            }
    }
}
