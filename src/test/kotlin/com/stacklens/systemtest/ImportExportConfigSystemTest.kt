package com.stacklens.systemtest

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ImportExportConfigSystemTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `POST export without password returns 400`() {
        mockMvc
            .post("/api/config/export") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"password": ""}"""
            }.andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `POST export with password returns encrypted JSON`() {
        mockMvc
            .post("/api/config/export") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"password": "testpassword123"}"""
            }.andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            }
    }

    @Test
    fun `POST import-from-path with non-existing file returns 400`() {
        mockMvc
            .post("/api/config/import-from-path") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"path": "/this/file/does/not/exist.json", "password": ""}"""
            }.andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `POST save-to-path with non-existing directory returns 400`() {
        mockMvc
            .post("/api/config/save-to-path") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"path": "/this/dir/does/not/exist/99", "filename": "test.json", "content": "{}"}"""
            }.andExpect {
                status { isBadRequest() }
            }
    }
}
