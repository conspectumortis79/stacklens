package com.stacklens.systemtest

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class UiPreferencesSystemTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    private val scratchSuffix: String = System.nanoTime().toString()

    private fun scratchKey(): String = "e2e_ui_pref_$scratchSuffix"

    @Test
    fun `GET ui-prefs returns a JSON object of saved preferences`() {
        mockMvc
            .get("/api/ui-prefs")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            }
    }

    @Test
    fun `GET ui-prefs listed value includes a previously written scratch key`() {
        val key = scratchKey()
        mockMvc
            .post("/api/ui-prefs/$key") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"value":"seeded"}"""
            }.andExpect { status { isOk() } }
        mockMvc
            .get("/api/ui-prefs")
            .andExpect {
                status { isOk() }
                jsonPath("$['$key']") { value("seeded") }
            }
    }

    @Test
    fun `POST ui-prefs with a body that is not valid JSON returns 4xx`() {
        mockMvc
            .post("/api/ui-prefs/${scratchKey()}") {
                contentType = MediaType.APPLICATION_JSON
                content = """{ this is not json """
            }.andExpect { status { is4xxClientError() } }
    }

    @Test
    fun `POST ui-prefs with an empty key (path edge case) yields a routing-style match on a non-empty key segment`() {
        mockMvc
            .post("/api/ui-prefs") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"value":"bulk-fallback"}"""
            }.andExpect { status { is4xxClientError() } }
    }

    @Test
    fun `docker-test-env key accepts the string true and returns ok without 5xx`() {
        mockMvc
            .post("/api/ui-prefs/dev_docker_test_env") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"value":"true"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.ok") { value(true) }
            }
        mockMvc
            .post("/api/ui-prefs/dev_docker_test_env") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"value":"false"}"""
            }.andExpect { status { isOk() } }
    }
}
