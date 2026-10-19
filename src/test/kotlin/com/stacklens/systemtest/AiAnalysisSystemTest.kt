package com.stacklens.systemtest

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class AiAnalysisSystemTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `GET analysis for unknown session returns status NONE`() {
        mockMvc
            .get("/api/sessions/999999/analysis")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.status") { value("NONE") }
            }
    }

    @Test
    fun `POST analyze for unknown session returns 404`() {
        mockMvc
            .post("/api/sessions/999999/analyze") {
                contentType = MediaType.APPLICATION_JSON
                content = """{}"""
            }.andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun `POST save-diagram with invalid directory returns 400`() {
        mockMvc
            .post("/api/sessions/999999/analysis/save-diagram") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"path": "/this/dir/does/not/exist/99", "filename": "test.mmd", "content": "graph TD; A-->B"}"""
            }.andExpect {
                status { isBadRequest() }
            }
    }
}
