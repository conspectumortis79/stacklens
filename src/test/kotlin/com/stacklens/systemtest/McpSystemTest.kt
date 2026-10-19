package com.stacklens.systemtest

import org.junit.jupiter.api.BeforeEach
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
class McpSystemTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @BeforeEach
    fun cleanMcpConfigs() {
        val ids =
            mockMvc
                .get("/api/config/mcp")
                .andReturn()
                .response.contentAsString
                .let { Regex("\"id\":(\\d+)").findAll(it).map { m -> m.groupValues[1] }.toList() }
        ids.forEach { mockMvc.delete("/api/config/mcp/$it") }
    }

    @Test
    fun `GET mcp configs returns JSON list`() {
        mockMvc
            .get("/api/config/mcp")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            }
    }

    @Test
    fun `POST mcp config creates new configuration and DELETE removes it`() {
        val createdId =
            mockMvc
                .post("/api/config/mcp") {
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        """
                        {
                          "name": "System Test MCP",
                          "baseUrl": "http://localhost:7456",
                          "authToken": "secret",
                          "active": false,
                          "routeJira": true,
                          "routeConfluence": true,
                          "routeGitlab": true,
                          "routeGithub": true,
                          "routeGrafana": true,
                          "routeDatabase": true
                        }
                        """.trimIndent()
                }.andExpect {
                    status { isOk() }
                    content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                    jsonPath("$.name") { value("System Test MCP") }
                    jsonPath("$.authToken") { value("") }
                    jsonPath("$.authTokenSet") { value(true) }
                }.andReturn()
                .response.contentAsString
                .let { Regex("\"id\":(\\d+)").find(it)!!.groupValues[1] }

        mockMvc
            .delete("/api/config/mcp/$createdId")
            .andExpect { status { isNoContent() } }
    }

    @Test
    fun `DELETE mcp config for unknown ID returns 404`() {
        mockMvc
            .delete("/api/config/mcp/999999")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `POST test-connection for unknown ID returns 404`() {
        mockMvc
            .post("/api/config/mcp/999999/test")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `GET mcp status returns active flag and per-source routes`() {
        mockMvc
            .get("/api/mcp/status")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.active") { exists() }
                jsonPath("$.routes") { exists() }
                jsonPath("$.routes.jira") { exists() }
                jsonPath("$.routes.confluence") { exists() }
                jsonPath("$.routes.gitlab") { exists() }
                jsonPath("$.routes.github") { exists() }
                jsonPath("$.routes.grafana") { exists() }
                jsonPath("$.routes.database") { exists() }
                jsonPath("$.processRunning") { exists() }
            }
    }

    @Test
    fun `GET mcp tools without active config returns 400`() {
        mockMvc
            .get("/api/mcp/tools")
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `POST mcp call without tool name returns ok=false when active config exists`() {
        mockMvc
            .post("/api/config/mcp") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    { "name": "Call Test MCP", "baseUrl": "http://localhost:7456", "active": true }
                    """.trimIndent()
            }.andExpect { status { isOk() } }
        mockMvc
            .post("/api/mcp/call") {
                contentType = MediaType.APPLICATION_JSON
                content = """{ "tool": "", "args": {} }"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.ok") { value(false) }
                jsonPath("$.text") { value("tool is required") }
            }
    }

    @Test
    fun `POST mcp call without active config returns 400`() {
        mockMvc
            .post("/api/mcp/call") {
                contentType = MediaType.APPLICATION_JSON
                content = """{ "tool": "any", "args": {} }"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.ok") { value(false) }
            }
    }

    @Test
    fun `POST sync-from-stacklens creates an MCP config from the current connections`() {
        mockMvc
            .post("/api/config/mcp/sync-from-stacklens")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.id") { exists() }
                jsonPath("$.name") { exists() }
                jsonPath("$.active") { value(true) }
            }
    }

    @Test
    fun `POST sync-from-stacklens does not overwrite a user-maintained MCP config`() {
        val createdId =
            mockMvc
                .post("/api/config/mcp") {
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        """
                        {
                          "name": "MyMCP",
                          "baseUrl": "http://my-mcp.example:9999",
                          "authToken": "user-secret",
                          "active": true
                        }
                        """.trimIndent()
                }.andReturn()
                .response.contentAsString
                .let { Regex("\"id\":(\\d+)").find(it)!!.groupValues[1] }

        mockMvc.post("/api/config/mcp/sync-from-stacklens").andExpect { status { isOk() } }

        mockMvc
            .get("/api/config/mcp")
            .andExpect { status { isOk() } }
            .andReturn()
            .response.contentAsString
            .let { body ->
                check(body.contains("\"id\":$createdId")) { "user row $createdId disappeared: $body" }
                check(body.contains("\"name\":\"MyMCP\"")) { "user row name was overwritten: $body" }
                check(body.contains("http://my-mcp.example:9999")) {
                    "user row baseUrl was overwritten: $body"
                }
            }
    }

    @Test
    fun `GET mcp process status returns running flag`() {
        mockMvc
            .get("/api/config/mcp/process/status")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.running") { exists() }
            }
    }

    @Test
    fun `POST mcp process start without any config returns 400 when DB is empty`() {
        mockMvc
            .post("/api/config/mcp/process/start")
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `POST mcp process stop returns status with running=false`() {
        mockMvc
            .post("/api/config/mcp/process/stop")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.running") { value(false) }
            }
    }
}
