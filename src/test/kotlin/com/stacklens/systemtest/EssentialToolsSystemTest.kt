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
class EssentialToolsSystemTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `GET tools status returns platform info and list of tools`() {
        mockMvc
            .get("/api/tools/status")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.platform") { exists() }
                jsonPath("$.platform.os") { exists() }
                jsonPath("$.platform.supported") { exists() }
                jsonPath("$.tools") { exists() }
                jsonPath("$.tools.length()") { value(2) }
                jsonPath("$.tools[0].id") { exists() }
                jsonPath("$.tools[0].installed") { exists() }
                jsonPath("$.tools[1].id") { exists() }
                jsonPath("$.tools[1].installed") { exists() }
            }
    }

    @Test
    fun `POST tools install without body defaults to gh and returns result`() {
        mockMvc
            .post("/api/tools/install")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.success") { exists() }
            }
    }

    @Test
    fun `POST tools install with tool gh returns result`() {
        mockMvc
            .post("/api/tools/install") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"tool": "gh"}"""
            }.andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.success") { exists() }
            }
    }
}
