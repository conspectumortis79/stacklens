package com.stacklens.systemtest

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class DockerTestSystemTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `GET docker-test info always returns 200 with status fields`() {
        mockMvc
            .get("/api/docker-test/info")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.dockerAvailable") { exists() }
                jsonPath("$.found") { exists() }
                jsonPath("$.composeFile") { exists() }
            }
    }
}
