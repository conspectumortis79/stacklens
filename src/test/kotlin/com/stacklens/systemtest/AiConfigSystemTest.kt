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
class AiConfigSystemTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `GET ai-config returns current AI configuration`() {
        mockMvc
            .get("/api/config/ai")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.githubTokenSet") { exists() }
                jsonPath("$.model") { exists() }
            }
    }

    @Test
    fun `PUT ai-config saves configuration`() {
        mockMvc
            .put("/api/config/ai") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"model": "gpt-4o", "availableModels": []}"""
            }.andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.model") { value("gpt-4o") }
            }
    }

    @Test
    fun `GET language returns current language`() {
        mockMvc
            .get("/api/config/language")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.language") { exists() }
            }
    }

    @Test
    fun `PUT language sets language to de`() {
        mockMvc
            .put("/api/config/language") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"language": "de"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.language") { value("de") }
            }
    }

    @Test
    fun `PUT language sets language to en`() {
        mockMvc
            .put("/api/config/language") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"language": "en"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.language") { value("en") }
            }
    }

    @Test
    fun `GET ai-config returns ollamaNumCtx field`() {
        mockMvc
            .get("/api/config/ai")
            .andExpect {
                status { isOk() }
                jsonPath("$.ollamaNumCtx") { exists() }
            }
    }

    @Test
    fun `PUT ai-config saves and returns ollamaNumCtx`() {
        mockMvc
            .put("/api/config/ai") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"model": "gpt-4o", "ollamaNumCtx": 65536}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.ollamaNumCtx") { value(65536) }
            }
    }

    @Test
    fun `POST ai-test-ollama returns JSON with success field`() {
        mockMvc
            .post("/api/config/ai/test-ollama") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"ollamaUrl": "http://localhost:11434"}"""
            }.andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.success") { exists() }
            }
    }

    @Test
    fun `PUT ai-config accepts claudecode provider and round-trips the API key`() {
        mockMvc
            .put("/api/config/ai") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"aiProvider": "claudecode", "model": "claude-sonnet-4-5", "claudeCodeApiKey": "sk-ant-test"}"""
            }.andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.aiProvider") { value("claudecode") }
                jsonPath("$.model") { value("claude-sonnet-4-5") }
                jsonPath("$.claudeCodeApiKeySet") { value(true) }
                jsonPath("$.claudeCodeApiKey") { value("••••••••") }
            }
    }

    @Test
    fun `GET ai-config returns claudeCodeApiKeySet field`() {
        mockMvc
            .get("/api/config/ai")
            .andExpect {
                status { isOk() }
                jsonPath("$.claudeCodeApiKeySet") { exists() }
            }
    }

    @Test
    fun `POST ai-test-claudecode returns JSON with success field`() {
        mockMvc
            .post("/api/config/ai/test-claudecode") {
                contentType = MediaType.APPLICATION_JSON
                content = """{}"""
            }.andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.success") { exists() }
            }
    }
}
