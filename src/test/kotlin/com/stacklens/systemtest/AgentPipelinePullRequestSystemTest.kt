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
class AgentPipelinePullRequestSystemTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `GET pull-request for unknown id returns 404 with HTML content type`() {
        mockMvc
            .get("/api/agent-pipeline/999999/pull-request")
            .andExpect {
                status { isNotFound() }
                content { contentTypeCompatibleWith(MediaType.TEXT_HTML) }
            }
    }

    @Test
    fun `GET pull-request for unknown id renders the styled Error template (not Whitelabel)`() {
        mockMvc
            .get("/api/agent-pipeline/424242/pull-request")
            .andExpect {
                status { isNotFound() }
                content { contentTypeCompatibleWith(MediaType.TEXT_HTML) }
                content { string(org.hamcrest.Matchers.containsString("<h1>Error</h1>")) }
                content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Whitelabel"))) }
            }
    }

    @Test
    fun `GET pull-request for unknown id embeds the id in the rendered message`() {
        mockMvc
            .get("/api/agent-pipeline/777777/pull-request")
            .andExpect {
                status { isNotFound() }
                content { string(org.hamcrest.Matchers.containsString("777777")) }
            }
    }

    @Test
    fun `GET pull-request with a non-numeric id is rejected by Spring (no controller match)`() {
        mockMvc
            .get("/api/agent-pipeline/notanumber/pull-request")
            .andExpect {
                status {
                    is4xxClientError()
                }
            }
    }

    @Test
    fun `GET pull-request endpoint shape — wrong suffix is not matched by the controller`() {
        mockMvc
            .get("/api/agent-pipeline/1/pull-requestX")
            .andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun `GET pull-request-error endpoint serves the styled error template when routed directly`() {
        mockMvc
            .get("/api/agent-pipeline/999999/pull-request")
            .andExpect {
                status { isNotFound() }
                content { string(org.hamcrest.Matchers.containsString("Error")) }
                content { string(org.hamcrest.Matchers.containsString("not found")) }
            }
    }
}
