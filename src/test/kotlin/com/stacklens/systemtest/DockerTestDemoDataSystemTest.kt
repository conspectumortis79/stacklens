package com.stacklens.systemtest

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
class DockerTestDemoDataSystemTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `GET data-files returns a JSON array (possibly empty)`() {
        mockMvc
            .get("/api/docker-test/data-files")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$") { isArray() }
            }
    }

    @Test
    fun `GET data-file with traversal path returns 400`() {
        mockMvc
            .get("/api/docker-test/data-file") {
                param("path", "../../../../etc/passwd")
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `GET data-file with empty path returns 400`() {
        mockMvc
            .get("/api/docker-test/data-file")
            .andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `GET data-file for a non-existent file returns 400`() {
        mockMvc
            .get("/api/docker-test/data-file") {
                param("path", "/tmp/stacklens-systemtest-definitely-not-here-xyz.json")
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `PUT data-file with traversal path returns 400`() {
        mockMvc
            .put("/api/docker-test/data-file") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"path": "../../../../tmp/evil.json", "content": "x"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `PUT data-file with empty body fields returns 400`() {
        mockMvc
            .put("/api/docker-test/data-file") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"path": "", "content": ""}"""
            }.andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `PUT data-file without path field returns 400`() {
        mockMvc
            .put("/api/docker-test/data-file") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"content": "irrelevant"}"""
            }.andExpect {
                status { isBadRequest() }
            }
    }
}
