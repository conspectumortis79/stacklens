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
class ToolingSystemTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `GET tooling returns list of all tools`() {
        mockMvc
            .get("/api/tooling")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            }
    }

    @Test
    fun `GET tool by unknown id returns 404`() {
        mockMvc
            .get("/api/tooling/999999")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `GET tool status by unknown id returns 404`() {
        mockMvc
            .get("/api/tooling/999999/status")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `POST tooling with blank name returns 400`() {
        mockMvc
            .post("/api/tooling") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"name": "   ", "scriptContent": "println(\"Hello\")"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `POST tooling with blank scriptContent returns 400`() {
        mockMvc
            .post("/api/tooling") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"name": "My Tool", "scriptContent": ""}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `POST tooling with null scriptContent returns 400`() {
        mockMvc
            .post("/api/tooling") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"name": "My Tool"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `DELETE tool by unknown id returns no content without error`() {
        mockMvc
            .delete("/api/tooling/999999")
            .andExpect { status { isNoContent() } }
    }

    @Test
    fun `DELETE tool record by unknown id returns no content without error`() {
        mockMvc
            .delete("/api/tooling/999999/record")
            .andExpect { status { isNoContent() } }
    }

    @Test
    fun `POST cancel tool by unknown id returns no content`() {
        mockMvc
            .post("/api/tooling/999999/cancel")
            .andExpect { status { isNoContent() } }
    }

    @Test
    fun `PUT tool code for unknown id returns 404`() {
        mockMvc
            .put("/api/tooling/999999/code") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"code": "println(\"Hello\")"}"""
            }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `PUT tool code with blank code returns 400`() {
        mockMvc
            .put("/api/tooling/999999/code") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"code": "   "}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `PUT tool prompt for unknown id returns 404`() {
        mockMvc
            .put("/api/tooling/999999/prompt") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"prompt": "Make this tool do X."}"""
            }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `PUT tool prompt with blank prompt returns 400`() {
        mockMvc
            .put("/api/tooling/999999/prompt") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"prompt": ""}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `POST ai-rework for unknown id returns 404`() {
        mockMvc
            .post("/api/tooling/999999/ai-rework") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"instructions": "Fix the bug on line 5."}"""
            }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `POST ai-rework with blank instructions returns 400`() {
        mockMvc
            .post("/api/tooling/999999/ai-rework") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"instructions": "   "}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `GET export for unknown tool id returns 404`() {
        mockMvc
            .get("/api/tooling/999999/export")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `GET files browse returns current directory contents`() {
        mockMvc
            .get("/api/tooling/files/browse")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.currentPath") { exists() }
                jsonPath("$.items") { exists() }
            }
    }

    @Test
    fun `GET files browse with non-existent path returns 400`() {
        mockMvc
            .get("/api/tooling/files/browse?path=/this/path/does/not/exist/stacklens")
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `POST import-base64 with invalid filename returns 400`() {
        mockMvc
            .post("/api/tooling/import-base64") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"name": "My Tool", "filename": "tool.txt", "base64": "dGVzdA=="}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `POST import-base64 with blank name returns 400`() {
        mockMvc
            .post("/api/tooling/import-base64") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"name": "", "filename": "tool.jar", "base64": "dGVzdA=="}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `POST import-local with non-existent file path returns 400`() {
        mockMvc
            .post("/api/tooling/import-local") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"name": "My Tool", "path": "/tmp/does_not_exist_stacklens.jar"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `POST run for unknown tool returns 4xx`() {
        val result =
            mockMvc
                .post("/api/tooling/999999/run") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"parameters": {}}"""
                }.andReturn()
        assert(result.response.status in 400..499) { "unexpected status ${result.response.status}" }
    }

    @Test
    fun `POST run-prompt for unknown tool returns 4xx`() {
        val result =
            mockMvc
                .post("/api/tooling/999999/run-prompt") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"prompt": "Run something"}"""
                }.andReturn()
        assert(result.response.status in 400..499) { "unexpected status ${result.response.status}" }
    }

    @Test
    fun `POST save-to-path for unknown tool returns 4xx`() {
        mockMvc
            .post("/api/tooling/999999/save-to-path") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"path": "/tmp"}"""
            }.andExpect {
                status { is4xxClientError() }
            }
    }

    @Test
    fun `POST import without name does not return 5xx`() {
        val result =
            mockMvc
                .post("/api/tooling/import") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"filename": "tool.jar", "base64": "dGVzdA=="}"""
                }.andReturn()
        assert(result.response.status < 500) { "Endpoint /api/tooling/import returned 5xx: ${result.response.status}" }
    }
}
