package com.stacklens.systemtest

import com.stacklens.adapter.inbound.rest.AppLifecycleController
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doNothing
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class AppLifecycleSystemTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoSpyBean
    lateinit var appLifecycleController: AppLifecycleController

    @BeforeEach
    fun stubRealShutdown() {
        doNothing().`when`(appLifecycleController).scheduleShutdown()
    }

    @Test
    fun `POST quit returns 200 with shutting-down status`() {
        mockMvc
            .post("/api/quit")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.status") { value("shutting down") }
            }
    }

    @Test
    fun `POST quit triggers scheduleShutdown exactly once`() {
        mockMvc
            .post("/api/quit")
            .andExpect {
                status { isOk() }
            }
        org.mockito.Mockito
            .verify(appLifecycleController, org.mockito.Mockito.times(1))
            .scheduleShutdown()
    }
}
