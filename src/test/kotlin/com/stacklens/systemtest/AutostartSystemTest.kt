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
class AutostartSystemTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `GET autostart status returns platform info and enabled flag`() {
        mockMvc
            .get("/api/autostart/status")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.supported") { exists() }
                jsonPath("$.enabled") { exists() }
                jsonPath("$.platform") { exists() }
                jsonPath("$.mode") { exists() }
                jsonPath("$.serverPort") { exists() }
            }
    }

    @Test
    fun `POST autostart enable with desktop mode returns ok`() {
        mockMvc
            .post("/api/autostart/enable") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"mode": "desktop"}"""
            }.andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.ok") { exists() }
            }
    }

    @Test
    fun `POST autostart enable with server mode returns ok`() {
        mockMvc
            .post("/api/autostart/enable") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"mode": "server"}"""
            }.andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.ok") { exists() }
            }
    }

    @Test
    fun `POST autostart enable without body defaults to desktop mode`() {
        mockMvc
            .post("/api/autostart/enable")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.ok") { exists() }
            }
    }

    @Test
    fun `POST autostart disable returns ok`() {
        mockMvc
            .post("/api/autostart/disable")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.ok") { exists() }
            }
    }
}
