package com.stacklens.systemtest

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class JiraTicketImplementationSystemTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `GET list returns empty list or valid JSON list`() {
        mockMvc
            .get("/api/ticket-implementation")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            }
    }

    @Test
    fun `DELETE for unknown analysis returns 404`() {
        mockMvc
            .delete("/api/ticket-implementation/999999")
            .andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun `DELETE history deletes all completed analyses`() {
        mockMvc
            .delete("/api/ticket-implementation/history")
            .andExpect {
                status { isOk() }
                jsonPath("$.deleted") { value(true) }
            }
    }
}
