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
class JwtAnalysisSystemTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `POST analyse with empty token returns 400`() {
        mockMvc
            .post("/api/jwt-analyse") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"token": ""}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `POST analyse with invalid token returns 400`() {
        mockMvc
            .post("/api/jwt-analyse") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"token": "not.a.valid.token"}"""
            }.andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `POST analyse with valid JWT returns analysis result`() {
        val jwt =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9" +
                ".eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ" +
                ".SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"
        mockMvc
            .post("/api/jwt-analyse") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"token": "$jwt"}"""
            }.andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            }
    }

    @Test
    fun `GET jwt-analysis page returns HTML response`() {
        mockMvc
            .get("/jwt-analysis")
            .andExpect {
                status { isOk() }
            }
    }
}
