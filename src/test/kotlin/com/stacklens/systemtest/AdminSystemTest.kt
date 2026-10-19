package com.stacklens.systemtest

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class AdminSystemTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `POST factory-reset-db resets the database and returns success`() {
        mockMvc
            .post("/api/admin/factory-reset-db")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.success") { value(true) }
            }
    }

    @Test
    fun `POST factory-reset-db clears AI configuration including OpenAI key and model`() {
        mockMvc
            .put("/api/config/ai") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"model": "gpt-4o", "openAiApiKey": "sk-test-key-1234", "aiProvider": "openai"}"""
            }.andExpect { status { isOk() } }

        mockMvc
            .post("/api/admin/factory-reset-db")
            .andExpect {
                status { isOk() }
                jsonPath("$.success") { value(true) }
            }

        mockMvc
            .get("/api/config/ai")
            .andExpect {
                status { isOk() }
                jsonPath("$.model") { value("") }
                jsonPath("$.openAiApiKeySet") { value(false) }
                jsonPath("$.aiProvider") { value("copilot") }
            }
    }
}
