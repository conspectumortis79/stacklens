package com.stacklens.systemtest

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.put

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class AgentFileSystemTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `GET agents pipeline returns non-empty list`() {
        mockMvc
            .get("/api/agents/pipeline")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$") { isArray() }
                jsonPath("$[0].name") { exists() }
                jsonPath("$[0].label") { exists() }
            }
    }

    @Test
    fun `GET agent by valid name returns content and isCustom flag`() {
        mockMvc
            .get("/api/agents/pipeline/nora")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.name") { value("nora") }
                jsonPath("$.content") { exists() }
                jsonPath("$.isCustom") { exists() }
                jsonPath("$.defaultContent") { exists() }
            }
    }

    @Test
    fun `GET agent by invalid name returns 404`() {
        mockMvc
            .get("/api/agents/pipeline/unknown-agent-xyz")
            .andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun `PUT agent with missing content returns 400`() {
        mockMvc
            .put("/api/agents/pipeline/nora") {
                contentType = MediaType.APPLICATION_JSON
                content = """{}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `PUT agent with blank content returns 400`() {
        mockMvc
            .put("/api/agents/pipeline/nora") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"content": "   "}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `PUT agent by invalid name returns 404`() {
        mockMvc
            .put("/api/agents/pipeline/unknown-agent-xyz") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"content": "some content"}"""
            }.andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun `DELETE agent resets to default and returns isCustom false`() {
        mockMvc
            .delete("/api/agents/pipeline/nora")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.ok") { value(true) }
                jsonPath("$.isCustom") { value(false) }
                jsonPath("$.content") { exists() }
            }
    }

    @Test
    fun `DELETE agent by invalid name returns 404`() {
        mockMvc
            .delete("/api/agents/pipeline/unknown-agent-xyz")
            .andExpect {
                status { isNotFound() }
            }
    }
}
