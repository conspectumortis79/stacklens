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
class AppApiSystemTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `GET ui-pref for unknown key returns empty value`() {
        mockMvc
            .get("/api/ui-prefs/non_existing_key_xyz")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.value") { value("") }
            }
    }

    @Test
    fun `POST ui-pref saves value and GET returns it`() {
        val key = "test_sidebar_state"
        mockMvc
            .post("/api/ui-prefs/$key") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"value": "collapsed"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.ok") { value(true) }
            }

        mockMvc
            .get("/api/ui-prefs/$key")
            .andExpect {
                status { isOk() }
                jsonPath("$.value") { value("collapsed") }
            }
    }

    @Test
    fun `POST ui-pref overwrites existing value`() {
        val key = "test_theme_pref"
        mockMvc.post("/api/ui-prefs/$key") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"value": "modern"}"""
        }
        mockMvc
            .post("/api/ui-prefs/$key") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"value": "dark"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.ok") { value(true) }
            }

        mockMvc
            .get("/api/ui-prefs/$key")
            .andExpect {
                status { isOk() }
                jsonPath("$.value") { value("dark") }
            }
    }

    @Test
    fun `POST ui-pref without value field returns 400`() {
        mockMvc
            .post("/api/ui-prefs/some_key") {
                contentType = MediaType.APPLICATION_JSON
                content = """{}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `POST open-url with non-http scheme returns 400`() {
        mockMvc
            .post("/api/open-url") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"url": "file:///etc/passwd"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `POST open-url with missing url field returns 400`() {
        mockMvc
            .post("/api/open-url") {
                contentType = MediaType.APPLICATION_JSON
                content = """{}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `POST open-url with javascript scheme returns 400`() {
        mockMvc
            .post("/api/open-url") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"url": "javascript:alert(1)"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }
}
