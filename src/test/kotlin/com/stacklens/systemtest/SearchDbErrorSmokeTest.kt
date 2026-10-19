package com.stacklens.systemtest

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.net.ServerSocket

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class SearchDbErrorSmokeTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    private var createdDbConfigId: Long? = null
    private var createdSessionId: Long? = null

    @Test
    fun `search with one unreachable database persists the connection error and still completes`() {
        val refusedPort =
            ServerSocket(0).use { socket ->
                socket.localPort
            }

        val dbConfigName = "smoke-broken-db-${System.currentTimeMillis()}"
        val dbConfigJson =
            """
            {
              "name": "$dbConfigName",
              "dbType": "POSTGRESQL",
              "host": "127.0.0.1",
              "port": $refusedPort,
              "databaseName": "does_not_matter",
              "username": "smoke",
              "password": "smoke",
              "schemas": "",
              "active": true
            }
            """.trimIndent()

        val dbConfigResult =
            mockMvc
                .post("/api/config/database") {
                    contentType = MediaType.APPLICATION_JSON
                    content = dbConfigJson
                }.andExpect { status { isOk() } }
                .andExpect { jsonPath("$.id") { exists() } }
                .andReturn()
        val dbConfigId =
            objectMapper.readTree(dbConfigResult.response.contentAsString).get("id").asLong()
        createdDbConfigId = dbConfigId

        val searchJson =
            """
            {
              "keywords": ["smoke-keyword"],
              "gitlabRepositories": [],
              "jiraProjects": [],
              "jiraComponents": [],
              "jiraLabels": [],
              "confluenceWebsites": [],
              "grafanaPages": [],
              "pipelineFilters": [],
              "includeGitlab": false,
              "includeDatabase": true,
              "includePipeline": false,
              "includeConfluence": false,
              "includeGrafana": false,
              "includeJira": false,
              "includeContextMap": false,
              "crossReferenceEnabled": false,
              "gitlabConfigIds": [],
              "databaseConfigIds": [$dbConfigId],
              "confluenceConfigIds": [],
              "grafanaConfigIds": [],
              "jiraConfigIds": []
            }
            """.trimIndent()

        val startResult =
            mockMvc
                .post("/api/search") {
                    contentType = MediaType.APPLICATION_JSON
                    content = searchJson
                }.andExpect { status { isOk() } }
                .andExpect { jsonPath("$.sessionId") { exists() } }
                .andReturn()
        val sessionId = objectMapper.readTree(startResult.response.contentAsString).get("sessionId").asLong()
        createdSessionId = sessionId

        val terminalStatus = waitForTerminalStatus(sessionId, timeoutMs = 60_000)
        assertEquals(
            "COMPLETED",
            terminalStatus,
            "Search should still complete when its only configured DB is unreachable " +
                "(the broken DB is a recoverable failure, not a search-fatal one).",
        )

        val statusResult =
            mockMvc
                .get("/api/search/$sessionId/status")
                .andExpect { status { isOk() } }
                .andExpect { jsonPath("$.status") { value("COMPLETED") } }
                .andReturn()
        val statusJson = objectMapper.readTree(statusResult.response.contentAsString)
        val dbErrors = statusJson.get("databaseErrors")
        assertNotNull(dbErrors, "status JSON must include the databaseErrors field")
        assertTrue(dbErrors.isArray, "databaseErrors must be an array")
        assertEquals(1, dbErrors.size(), "expected exactly one failed DB")

        val entry = dbErrors.get(0)
        assertEquals(dbConfigId, entry.get("configId").asLong(), "configId should match the broken DB")
        assertEquals(dbConfigName, entry.get("configName").asText(), "configName should be echoed back")
        assertEquals("127.0.0.1", entry.get("host").asText(), "host should be the loopback we configured")
        assertEquals("does_not_matter", entry.get("databaseName").asText())
        assertEquals("POSTGRESQL", entry.get("dbType").asText())
        val errorMessage = entry.get("errorMessage").asText()
        assertTrue(
            errorMessage.isNotBlank(),
            "errorMessage must be non-empty (the JDBC layer always surfaces something for a refused connection)",
        )
        assertTrue(
            errorMessage.length <= 500,
            "errorMessage must be truncated to ≤500 chars, was ${errorMessage.length}",
        )
    }

    private fun waitForTerminalStatus(
        sessionId: Long,
        timeoutMs: Long,
    ): String {
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastStatus: String? = null
        while (System.currentTimeMillis() < deadline) {
            val result =
                mockMvc
                    .get("/api/search/$sessionId/status")
                    .andExpect { status { isOk() } }
                    .andReturn()
            val statusStr = objectMapper.readTree(result.response.contentAsString).get("status").asText()
            lastStatus = statusStr
            if (statusStr == "COMPLETED" || statusStr == "FAILED" || statusStr == "CANCELLED") {
                return statusStr
            }
            Thread.sleep(500)
        }
        error("Session $sessionId did not reach a terminal state within ${timeoutMs}ms; last status was $lastStatus")
    }

    @org.junit.jupiter.api.AfterEach
    fun cleanup() {
        createdSessionId?.let { id ->
            try {
                mockMvc.delete("/api/sessions/$id")
            } catch (_: Exception) {
            }
        }
        createdDbConfigId?.let { id ->
            try {
                mockMvc.delete("/api/config/database/$id")
            } catch (_: Exception) {
            }
        }
        createdSessionId = null
        createdDbConfigId = null
    }
}
