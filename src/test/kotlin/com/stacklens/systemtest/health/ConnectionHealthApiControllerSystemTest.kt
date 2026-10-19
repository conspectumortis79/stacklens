package com.stacklens.systemtest.health

import com.stacklens.adapter.out.health.InMemoryConnectionHealthTracker
import org.junit.jupiter.api.BeforeEach
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
class ConnectionHealthApiControllerSystemTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var tracker: InMemoryConnectionHealthTracker

    @BeforeEach
    fun setUp() {
        tracker.reset()
    }

    @Test
    fun `GET health connections returns a snapshots array`() {
        mockMvc
            .get("/api/health/connections")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.snapshots") { exists() }
            }
    }

    @Test
    fun `POST probe with unknown kind returns 400`() {
        mockMvc
            .post("/api/health/connections/bogus/1/probe")
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `POST probe with unknown id returns 404`() {
        mockMvc
            .post("/api/health/connections/database/99999999/probe")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `HealthProbe endpoints do not crash on an empty configuration database`() {
        mockMvc
            .get("/api/health/connections")
            .andExpect {
                status { isOk() }
                jsonPath("$.snapshots.length()") { value(0) }
            }
    }
}
