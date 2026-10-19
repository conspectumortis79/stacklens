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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class DatabaseConfigSystemTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `GET database-configs returns empty list or valid JSON list`() {
        mockMvc
            .get("/api/config/database")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            }
    }

    @Test
    fun `GET database-types returns all supported database types`() {
        mockMvc
            .get("/api/config/database/types")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$[0].name") { exists() }
                jsonPath("$[0].defaultPort") { exists() }
            }
    }

    @Test
    fun `POST database-config creates new configuration`() {
        mockMvc
            .post("/api/config/database") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "name": "Test DB",
                      "dbType": "POSTGRESQL",
                      "host": "localhost",
                      "port": 5432,
                      "databaseName": "testdb",
                      "username": "testuser",
                      "password": "testpass",
                      "schemas": "",
                      "active": true
                    }
                    """.trimIndent()
            }.andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.name") { value("Test DB") }
            }
    }

    @Test
    fun `DELETE database-config for unknown ID returns 404`() {
        mockMvc
            .delete("/api/config/database/999999")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `POST test-connection for unknown ID returns 404`() {
        mockMvc
            .post("/api/config/database/999999/test")
            .andExpect { status { isNotFound() } }
    }
}
