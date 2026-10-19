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
class GrafanaConfigSystemTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `GET grafana-configs returns empty list or valid JSON list`() {
        mockMvc
            .get("/api/config/grafana")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            }
    }

    @Test
    fun `POST grafana-config creates new configuration`() {
        mockMvc
            .post("/api/config/grafana") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "name": "Test Grafana",
                      "baseUrl": "http://localhost:3000",
                      "username": "admin",
                      "password": "admin",
                      "apiToken": "",
                      "orgId": 1,
                      "scanPanelQueries": false,
                      "active": true
                    }
                    """.trimIndent()
            }.andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.name") { value("Test Grafana") }
            }
    }

    @Test
    fun `DELETE grafana-config for unknown ID returns 404`() {
        mockMvc
            .delete("/api/config/grafana/999999")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `POST test-connection for unknown ID returns 404`() {
        mockMvc
            .post("/api/config/grafana/999999/test")
            .andExpect { status { isNotFound() } }
    }
}
