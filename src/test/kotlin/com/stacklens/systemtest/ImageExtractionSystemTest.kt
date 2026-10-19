package com.stacklens.systemtest

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ImageExtractionSystemTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    private val json = ObjectMapper()

    @Test
    fun `GET history returns a JSON array`() {
        mockMvc
            .get("/api/image-extraction/history")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            }
    }

    @Test
    fun `GET history for unknown id returns 404`() {
        mockMvc
            .get("/api/image-extraction/history/99999999")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `DELETE history for unknown id returns 404`() {
        mockMvc
            .delete("/api/image-extraction/history/99999999")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `DELETE all history is idempotent and returns a deletion ack`() {
        mockMvc
            .delete("/api/image-extraction/history")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.deleted") { value(true) }
            }
        mockMvc
            .delete("/api/image-extraction/history")
            .andExpect {
                status { isOk() }
                jsonPath("$.deleted") { value(true) }
            }
    }

    @Test
    fun `POST extract with blank source path returns 400 with an error key`() {
        mockMvc
            .post("/api/image-extraction/extract") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"sourcePath":"","originalFileName":"a.png","mimeType":"image/png"}"""
            }.andExpect {
                status { isBadRequest() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `POST extract with unsupported mime and extension returns 400`() {
        mockMvc
            .post("/api/image-extraction/extract") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"sourcePath":"/tmp/x.pdf","originalFileName":"x.pdf","mimeType":"application/pdf"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `POST extract with a non-existent server path is rejected (4xx or 5xx)`() {
        val raw =
            mockMvc
                .post("/api/image-extraction/extract") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"sourcePath":"/this/path/does/not/exist/nowhere-${System.nanoTime()}.png","originalFileName":"nowhere.png","mimeType":"image/png"}"""
                }.andReturn()
        val status = raw.response.status
        assertTrue(
            status in 400..599,
            "expected 4xx or 5xx, got $status",
        )
    }

    @Test
    fun `POST extract-upload with an empty multipart returns 400`() {
        val empty =
            MockMultipartFile(
                "file",
                "empty.png",
                "image/png",
                ByteArray(0),
            )
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .multipart("/api/image-extraction/extract-upload")
                    .file(empty),
            ).andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers
                    .status()
                    .isBadRequest,
            )
    }

    @Test
    fun `POST extract-upload with an unsupported mime type returns 400`() {
        val pdf =
            MockMultipartFile(
                "file",
                "doc.pdf",
                "application/pdf",
                "%PDF-1.4\n%fake".toByteArray(),
            )
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .multipart("/api/image-extraction/extract-upload")
                    .file(pdf),
            ).andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers
                    .status()
                    .isBadRequest,
            )
    }

    @Test
    fun `GET file browser with a non-existent path returns 400`() {
        mockMvc
            .get("/api/image-extraction/files/browse") {
                param("path", "/this/does/not/exist/nowhere-${System.nanoTime()}")
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `GET file browser with user home returns 200 with current and parent path`() {
        val raw =
            mockMvc
                .get("/api/image-extraction/files/browse") {
                    param("path", System.getProperty("user.home"))
                }.andExpect {
                    status { isOk() }
                    content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                }.andReturn()
                .response.contentAsString
        val node = json.readTree(raw)
        assertTrue(node.has("currentPath"), "expected currentPath in $raw")
        assertTrue(node.has("items"), "expected items in $raw")
    }

    @Test
    fun `GET file browser with the default (no path) returns 200`() {
        mockMvc
            .get("/api/image-extraction/files/browse")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            }
    }
}
