package com.stacklens.systemtest

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.put

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class TextFilterConfigSystemTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    private fun getEnabled(): Boolean =
        mockMvc
            .get("/api/text-filter/config")
            .andExpect { status { isOk() } }
            .andReturn()
            .response.contentAsString
            .let { Regex("\"enabled\":(true|false)").find(it)?.groupValues?.get(1) == "true" }

    private fun setEnabled(value: Boolean) {
        mockMvc
            .put("/api/text-filter/config") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"enabled":$value}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.ok") { value(true) }
                jsonPath("$.enabled") { value(value) }
            }
    }

    @Test
    fun `GET text-filter config returns a JSON object with an enabled field`() {
        mockMvc
            .get("/api/text-filter/config")
            .andExpect { status { isOk() } }
            .andReturn()
            .let { raw ->
                val body = raw.response.contentAsString
                assertTrue(
                    body.contains("\"enabled\""),
                    "expected 'enabled' in response, got: $body",
                )
            }
    }

    @Test
    fun `PUT boolean true round-trips through GET`() {
        val original = getEnabled()
        try {
            setEnabled(true)
            assertEquals(true, getEnabled())
        } finally {
            setEnabled(original)
        }
    }

    @Test
    fun `PUT boolean false round-trips through GET`() {
        val original = getEnabled()
        try {
            setEnabled(false)
            assertEquals(false, getEnabled())
        } finally {
            setEnabled(original)
        }
    }

    @Test
    fun `PUT string 'true' is parsed as boolean true (loose-string contract)`() {
        val original = getEnabled()
        try {
            mockMvc
                .put("/api/text-filter/config") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"enabled":"true"}"""
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.enabled") { value(true) }
                }
            assertEquals(true, getEnabled())
        } finally {
            setEnabled(original)
        }
    }

    @Test
    fun `PUT string 'false' is parsed as boolean false`() {
        val original = getEnabled()
        try {
            mockMvc
                .put("/api/text-filter/config") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"enabled":"false"}"""
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.enabled") { value(false) }
                }
            assertEquals(false, getEnabled())
        } finally {
            setEnabled(original)
        }
    }

    @Test
    fun `PUT with a non-boolean enabled value is rejected with 400`() {
        mockMvc
            .put("/api/text-filter/config") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"enabled":123}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `PUT with a missing enabled field is rejected, not silently accepted`() {
        mockMvc
            .put("/api/text-filter/config") {
                contentType = MediaType.APPLICATION_JSON
                content = """{}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }
}
