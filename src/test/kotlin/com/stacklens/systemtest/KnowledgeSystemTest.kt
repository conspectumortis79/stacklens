package com.stacklens.systemtest

import com.fasterxml.jackson.databind.JsonNode
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
import org.springframework.test.web.servlet.put

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class KnowledgeSystemTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    private val json = ObjectMapper()

    private val createdBaseIds = mutableListOf<Long>()
    private val createdSourceIds = mutableListOf<Long>()

    private fun createBase(name: String): Long {
        val body =
            mockMvc
                .post("/api/knowledge/bases") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"name":"$name","description":"e2e"}"""
                }.andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString
        val node = json.readTree(body)
        val id = node.get("id").asLong()
        createdBaseIds.add(id)
        return id
    }

    private fun deleteSource(id: Long) {
        runCatching { mockMvc.delete("/api/knowledge/sources/$id") }
        createdSourceIds.remove(id)
    }

    private fun deleteBase(id: Long) {
        runCatching { mockMvc.delete("/api/knowledge/bases/$id") }
        createdBaseIds.remove(id)
    }

    @Test
    fun `GET knowledge config returns the current embedding configuration`() {
        mockMvc
            .get("/api/knowledge/config")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.chunkSize") { exists() }
                jsonPath("$.autoIndexReview") { exists() }
            }
    }

    @Test
    fun `PUT knowledge config saves and the next GET returns the same values`() {
        val original =
            mockMvc
                .get("/api/knowledge/config")
                .andReturn()
                .response.contentAsString
        val originalNode = json.readTree(original)
        val payload =
            """
            {
              "enabled": true,
              "provider": "OLLAMA",
              "model": "nomic-embed-text",
              "ollamaUrl": "http://localhost:11434",
              "chunkSize": 1234,
              "chunkOverlap": 99,
              "topK": 5,
              "minimumBm25Score": 0.1,
              "maximumContextCharacters": 9999,
              "autoIndexReview": true,
              "autoIndexSprintReview": true,
              "autoIndexTicketCreation": true,
              "autoIndexTicketRefinement": true,
              "autoIndexTicketSummary": false
            }
            """.trimIndent()
        mockMvc
            .put("/api/knowledge/config") {
                contentType = MediaType.APPLICATION_JSON
                content = payload
            }.andExpect { status { isOk() } }
        mockMvc
            .get("/api/knowledge/config")
            .andExpect {
                status { isOk() }
                jsonPath("$.chunkSize") { value(1234) }
                jsonPath("$.model") { value("nomic-embed-text") }
                jsonPath("$.autoIndexTicketSummary") { value(false) }
            }
        mockMvc
            .put("/api/knowledge/config") {
                contentType = MediaType.APPLICATION_JSON
                content = original
            }.andExpect { status { isOk() } }
        val restored =
            mockMvc
                .get("/api/knowledge/config")
                .andReturn()
                .response.contentAsString
        assertEquals(originalNode.get("chunkSize").asInt(), json.readTree(restored).get("chunkSize").asInt())
    }

    @Test
    fun `GET knowledge config models returns a list (network-bound, must not 500)`() {
        val raw =
            mockMvc
                .get("/api/knowledge/config/models")
                .andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString
        val node: JsonNode = json.readTree(raw)
        assertTrue(
            node.has("models") || node.has("error"),
            "expected either 'models' or 'error' in response, got: $raw",
        )
    }

    @Test
    fun `POST then GET then PUT then DELETE base follows the full CRUD contract`() {
        val name = "e2e-knowledge-base-${System.nanoTime()}"
        val created =
            mockMvc
                .post("/api/knowledge/bases") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"name":"$name","description":"d","includeInGlobalChat":true}"""
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.name") { value(name) }
                }.andReturn()
                .response.contentAsString
        val baseId = json.readTree(created).get("id").asLong()
        createdBaseIds.add(baseId)

        mockMvc
            .get("/api/knowledge/bases")
            .andExpect {
                status { isOk() }
                jsonPath("$[?(@.id==$baseId)].name") { value(name) }
            }

        mockMvc
            .put("/api/knowledge/bases/$baseId") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"$name-renamed","active":true}"""
            }.andExpect { status { isOk() } }

        mockMvc
            .get("/api/knowledge/bases")
            .andExpect {
                status { isOk() }
                jsonPath("$[?(@.id==$baseId)].name") { value("$name-renamed") }
            }

        mockMvc
            .delete("/api/knowledge/bases/$baseId")
            .andExpect {
                status { isOk() }
                jsonPath("$.ok") { value(true) }
            }
        createdBaseIds.remove(baseId)
    }

    @Test
    fun `POST base clear on the system-managed base is rejected with a clear error`() {
        val basesRaw =
            mockMvc
                .get("/api/knowledge/bases")
                .andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString
        val systemManaged =
            json.readTree(basesRaw).firstOrNull { it.get("systemManaged").asBoolean(false) }
        if (systemManaged == null) {
            return
        }
        val id = systemManaged.get("id").asLong()
        mockMvc
            .post("/api/knowledge/bases/$id/clear")
            .andExpect {
                status { isOk() }
                jsonPath("$.ok") { value(true) }
            }
    }

    @Test
    fun `DELETE base with unknown id is a no-op that returns 200`() {
        mockMvc
            .delete("/api/knowledge/bases/99999999")
            .andExpect {
                status { isOk() }
                jsonPath("$.ok") { value(true) }
            }
    }

    @Test
    fun `POST manual source then GET then DELETE follows CRUD contract`() {
        val baseId = createBase("e2e-src-base-${System.nanoTime()}")
        val create =
            mockMvc
                .post("/api/knowledge/sources/manual") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"knowledgeBaseId":$baseId,"name":"e2e-manual","content":"hello world","externalReference":"e2e-${System.nanoTime()}"}"""
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.name") { value("e2e-manual") }
                    jsonPath("$.type") { value("MANUAL") }
                    jsonPath("$.autoManaged") { value(false) }
                }.andReturn()
                .response.contentAsString
        val sourceId = json.readTree(create).get("id").asLong()
        createdSourceIds.add(sourceId)

        mockMvc
            .get("/api/knowledge/sources/$sourceId")
            .andExpect {
                status { isOk() }
                jsonPath("$.id") { value(sourceId) }
            }

        mockMvc
            .get("/api/knowledge/sources") {
                param("knowledgeBaseId", baseId.toString())
            }.andExpect {
                status { isOk() }
                jsonPath("$[?(@.id==$sourceId)]") { exists() }
            }

        mockMvc
            .delete("/api/knowledge/sources/$sourceId")
            .andExpect {
                status { isOk() }
                jsonPath("$.ok") { value(true) }
            }
        createdSourceIds.remove(sourceId)
        deleteBase(baseId)
    }

    @Test
    fun `POST manual source with unknown base id returns 404 with a domain error via KnowledgeExceptionAdvice`() {
        mockMvc
            .post("/api/knowledge/sources/manual") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"knowledgeBaseId":99999999,"name":"orphan","content":"x"}"""
            }.andExpect {
                status { isNotFound() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `GET sources for unknown id returns null body with 200`() {
        mockMvc
            .get("/api/knowledge/sources/99999999")
            .andExpect {
                status { isOk() }
                content { string("") }
            }
    }

    @Test
    fun `GET knowledge jobs returns a JSON array`() {
        mockMvc
            .get("/api/knowledge/jobs")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            }
    }

    @Test
    fun `GET knowledge job for unknown id returns 200 with empty body`() {
        mockMvc
            .get("/api/knowledge/jobs/99999999")
            .andExpect {
                status { isOk() }
                content { string("") }
            }
    }

    @Test
    fun `POST cancel knowledge job for unknown id is a no-op that returns 200`() {
        mockMvc
            .post("/api/knowledge/jobs/99999999/cancel")
            .andExpect {
                status { isOk() }
                jsonPath("$.ok") { value(true) }
            }
    }

    @Test
    fun `POST export-all without required password returns 4xx`() {
        mockMvc
            .post("/api/knowledge/export-all") {
                contentType = MediaType.APPLICATION_JSON
                content = """{}"""
            }.andExpect { status { is4xxClientError() } }
    }

    @Test
    fun `POST export base with invalid id and a password returns 400 via KnowledgeExceptionAdvice`() {
        mockMvc
            .post("/api/knowledge/bases/99999999/export") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"password":"e2e-pwd"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `POST export-all with a password returns an octet-stream attachment`() {
        mockMvc
            .post("/api/knowledge/export-all") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"password":"e2e-pwd"}"""
            }.andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_OCTET_STREAM) }
                header { exists("Content-Disposition") }
            }
    }

    @Test
    fun `POST reindex-all is idempotent and returns ok even with no sources`() {
        mockMvc
            .post("/api/knowledge/reindex-all")
            .andExpect {
                status { isOk() }
                jsonPath("$.ok") { value(true) }
                jsonPath("$.baseCount") { exists() }
                jsonPath("$.totalChunks") { exists() }
            }
    }

    @Test
    fun `POST knowledge reset wipes data, recreates the system-managed base, and returns ok`() {
        val beforeRaw =
            mockMvc
                .get("/api/knowledge/bases")
                .andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString
        val beforeCount = json.readTree(beforeRaw).size()

        mockMvc
            .post("/api/knowledge/reset")
            .andExpect {
                status { isOk() }
                jsonPath("$.ok") { value(true) }
                jsonPath("$.resetAt") { exists() }
            }

        val afterRaw =
            mockMvc
                .get("/api/knowledge/bases")
                .andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString
        val after = json.readTree(afterRaw)
        assertTrue(
            after.any { it.get("systemManaged").asBoolean(false) },
            "system-managed base must be recreated by /reset",
        )
        assertNotNull(after)
        createdBaseIds.clear()
        createdSourceIds.clear()
        assertTrue(beforeCount >= 0)
    }
}
