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
class GrafanaAnalysisSystemTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `GET demo-dashboard-json returns valid JSON dashboard`() {
        mockMvc
            .get("/api/grafana-analyse/demo-dashboard-json")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.dashboard") { exists() }
                jsonPath("$.dashboard.title") { value("Demo Monitoring Dashboard – server-node01") }
                jsonPath("$.dashboard.panels.length()") { value(8) }
                jsonPath("$.dashboard.panels[0].id") { value(1) }
                jsonPath("$.dashboard.panels[0].type") { value("timeseries") }
                jsonPath("$.dashboard.panels[0].title") { value("CPU-Auslastung") }
                jsonPath("$.dashboard.panels[4].id") { value(5) }
                jsonPath("$.dashboard.panels[4].type") { value("stat") }
                jsonPath("$.dashboard.panels[7].id") { value(8) }
            }
    }

    @Test
    fun `GET files-browse without parameters returns directory listing`() {
        mockMvc
            .get("/api/grafana-analyse/files/browse")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.currentPath") { exists() }
                jsonPath("$.items") { exists() }
            }
    }

    @Test
    fun `GET files-browse with invalid path returns 400`() {
        mockMvc
            .get("/api/grafana-analyse/files/browse") {
                param("path", "/this/path/does/not/exist/12345")
            }.andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `POST generate-dummy-data without JSON returns 400`() {
        mockMvc
            .post("/api/grafana-analyse/generate-dummy-data") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"dashboardJson": ""}"""
            }.andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `POST generate-dummy-data with invalid JSON returns 400`() {
        mockMvc
            .post("/api/grafana-analyse/generate-dummy-data") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"dashboardJson": "not-valid-json"}"""
            }.andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `POST rebuild-dashboard without JSON returns 400`() {
        mockMvc
            .post("/api/grafana-analyse/rebuild-dashboard") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"dashboardJson": "", "instructions": "test"}"""
            }.andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `POST rebuild-dashboard without instructions returns 400`() {
        mockMvc
            .post("/api/grafana-analyse/rebuild-dashboard") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"dashboardJson": "{}", "instructions": ""}"""
            }.andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `POST fetch-dashboard-json without URL returns 400`() {
        mockMvc
            .post("/api/grafana-analyse/fetch-dashboard-json") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"grafanaUrl": ""}"""
            }.andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `POST fetch-dashboard-json with non-http URL returns 400`() {
        mockMvc
            .post("/api/grafana-analyse/fetch-dashboard-json") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"grafanaUrl": "ftp://example.com/dashboard"}"""
            }.andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `GET metrics for unknown config returns 404`() {
        mockMvc
            .get("/api/grafana-analyse/metrics/999999") {
                param("dashboardUid", "demo")
            }.andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun `GET home-dashboard for unknown config returns 404`() {
        mockMvc
            .get("/api/grafana-analyse/home-dashboard/999999")
            .andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun `POST save-dashboard-json without content returns 400`() {
        mockMvc
            .post("/api/grafana-analyse/save-dashboard-json") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"content": "", "path": "/tmp", "filename": "test.json"}"""
            }.andExpect {
                status { isBadRequest() }
            }
    }
}
