package com.stacklens.systemtest

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.post

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class KiChatSystemTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `POST ki-chat with blank userMessage returns 200`() {
        mockMvc
            .post("/api/ki-chat") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"userMessage": ""}"""
            }.andExpect {
                status { isOk() }
            }
    }

    @Test
    fun `POST ki-chat with valid userMessage returns 200`() {
        mockMvc
            .post("/api/ki-chat") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"userMessage": "Was kann StackLens?", "tabId": "general", "tabLabel": "Allgemein", "chatId": 123456}"""
            }.andExpect {
                status { isOk() }
            }
    }

    @Test
    fun `DELETE ki-chat usage for a positive chat id returns 200`() {
        mockMvc
            .delete("/api/ki-chat/usage/123456")
            .andExpect {
                status { isOk() }
            }
    }

    @Test
    fun `DELETE ki-chat usage rejects zero`() {
        mockMvc
            .delete("/api/ki-chat/usage/0")
            .andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `POST ki-chat cancel for any tabId returns 200`() {
        mockMvc
            .post("/api/ki-chat/cancel/general") {
                contentType = MediaType.APPLICATION_JSON
                content = ""
            }.andExpect {
                status { isOk() }
            }
    }

    @Test
    fun `POST ki-chat cancel for unknown tabId returns 200`() {
        mockMvc
            .post("/api/ki-chat/cancel/non-existent-tab-xyz") {
                contentType = MediaType.APPLICATION_JSON
                content = ""
            }.andExpect {
                status { isOk() }
            }
    }
}
