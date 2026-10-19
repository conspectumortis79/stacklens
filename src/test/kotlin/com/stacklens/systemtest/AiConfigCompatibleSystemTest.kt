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
class AiConfigCompatibleSystemTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `PUT ai-config accepts the compatible provider and round-trips key and base url`() {
        mockMvc
            .put("/api/config/ai") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "aiProvider": "compatible",
                      "model": "qwen3.8-flash",
                      "compatibleApiKey": "sk-compatible-test",
                      "compatibleBaseUrl": "https://coding-intl.dashscope.aliyuncs.com/v1"
                    }
                    """.trimIndent()
            }.andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.aiProvider") { value("compatible") }
                jsonPath("$.model") { value("qwen3.8-flash") }
                jsonPath("$.compatibleApiKeySet") { value(true) }
                jsonPath("$.compatibleApiKey") { value("••••••••") }
                jsonPath("$.compatibleBaseUrl") { value("https://coding-intl.dashscope.aliyuncs.com/v1") }
            }
    }

    @Test
    fun `GET ai-config exposes the compatible fields`() {
        mockMvc
            .get("/api/config/ai")
            .andExpect {
                status { isOk() }
                jsonPath("$.compatibleApiKeySet") { exists() }
                jsonPath("$.compatibleBaseUrl") { exists() }
            }
    }

    @Test
    fun `POST ai-test-compatible without a stored key fails locally instead of calling the network`() {
        mockMvc
            .put("/api/config/ai") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"aiProvider": "compatible", "compatibleApiKey": ""}"""
            }.andExpect {
                status { isOk() }
            }

        mockMvc
            .post("/api/config/ai/test-compatible") {
                contentType = MediaType.APPLICATION_JSON
                content = """{}"""
            }.andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.success") { value(false) }
                jsonPath("$.error") { exists() }
            }
    }
}
