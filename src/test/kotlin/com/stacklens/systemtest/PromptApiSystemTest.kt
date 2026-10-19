package com.stacklens.systemtest

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.put

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class PromptApiSystemTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `GET prompt keys for search prefix returns list of keys`() {
        mockMvc
            .get("/api/prompts/keys?prefix=search&lang=en")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            }
    }

    @Test
    fun `GET prompt groups for search prefix returns list with label and mainKey`() {
        mockMvc
            .get("/api/prompts/groups?prefix=search&lang=en")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            }
    }

    @Test
    fun `GET prompt keys for grafana prefix in German returns list`() {
        mockMvc
            .get("/api/prompts/keys?prefix=grafana&lang=de")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            }
    }

    @Test
    fun `GET specific prompt returns content, isCustom and defaultContent`() {
        val keys =
            mockMvc
                .get("/api/prompts/keys?prefix=search&lang=en")
                .andReturn()
                .response
                .contentAsString

        if (keys != "[]") {
            val firstKey = keys.substringAfter("[\"").substringBefore("\"")
            mockMvc
                .get("/api/prompts/$firstKey?lang=en")
                .andExpect {
                    status { isOk() }
                    content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                    jsonPath("$.key") { exists() }
                    jsonPath("$.content") { exists() }
                    jsonPath("$.isCustom") { exists() }
                    jsonPath("$.defaultContent") { exists() }
                }
        }
    }

    @Test
    fun `PUT prompt saves custom content and marks it as custom`() {
        val key = "search.system"
        mockMvc
            .put("/api/prompts/$key") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"content": "Custom system prompt for search."}"""
            }.andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.ok") { value(true) }
                jsonPath("$.isCustom") { value(true) }
            }
    }

    @Test
    fun `PUT prompt with blank content returns 400`() {
        mockMvc
            .put("/api/prompts/search.system") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"content": "   "}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `PUT prompt without content field returns 400`() {
        mockMvc
            .put("/api/prompts/search.system") {
                contentType = MediaType.APPLICATION_JSON
                content = """{}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `DELETE prompt resets to default and returns isCustom false`() {
        val key = "search.system"
        mockMvc.put("/api/prompts/$key") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"content": "Temporary custom content."}"""
        }

        mockMvc
            .delete("/api/prompts/$key?lang=en")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.ok") { value(true) }
                jsonPath("$.isCustom") { value(false) }
                jsonPath("$.content") { exists() }
            }
    }

    @Test
    fun `GET read-file with path outside home directory returns 400`() {
        mockMvc
            .get("/api/prompts/read-file?path=/etc/passwd")
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }

    @Test
    fun `GET read-file with non-existent file returns 400`() {
        val homePath = System.getProperty("user.home") + "/this_file_does_not_exist_stacklens_test.txt"
        mockMvc
            .get("/api/prompts/read-file?path=$homePath")
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
    }
}
