package com.stacklens.systemtest

import org.junit.jupiter.api.Assertions.assertEquals
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
class LanguageConfigSystemTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `GET language returns a JSON object with a language key`() {
        mockMvc
            .get("/api/config/language")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.language") { exists() }
            }
    }

    @Test
    fun `GET language returns either en or de by default`() {
        val lang = currentLanguage()
        assertEquals(
            true,
            lang == "en" || lang == "de",
            "expected 'en' or 'de', got '$lang'",
        )
    }

    @Test
    fun `PUT language to en is accepted and round-trips`() {
        mockMvc
            .put("/api/config/language") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"language":"en"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.language") { value("en") }
            }
        assertEquals("en", currentLanguage())
    }

    @Test
    fun `PUT language to de is accepted and round-trips`() {
        mockMvc
            .put("/api/config/language") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"language":"de"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.language") { value("de") }
            }
        assertEquals("de", currentLanguage())
    }

    @Test
    fun `PUT language with an unrecognised value is coerced to en`() {
        mockMvc
            .put("/api/config/language") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"language":"fr"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.language") { value("en") }
            }
        assertEquals("en", currentLanguage())
    }

    private fun currentLanguage(): String =
        mockMvc
            .get("/api/config/language")
            .andExpect { status { isOk() } }
            .andReturn()
            .response.contentAsString
            .let { Regex("\"language\":\"([^\"]+)\"").find(it)?.groupValues?.get(1) ?: "" }
}
