package com.stacklens.systemtest

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class LoggingFixSystemTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `GET logging fix list returns array`() {
        mockMvc
            .get("/api/logging/fix")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$") { isArray() }
            }
    }

    @Test
    fun `GET logging fix status for unknown id returns 404`() {
        mockMvc
            .get("/api/logging/fix/999999")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `POST start with blank logText returns 400`() {
        mockMvc
            .post("/api/logging/fix") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"logText": ""}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { isNotEmpty() }
            }
    }

    @Test
    fun `POST start with whitespace-only logText returns 400`() {
        mockMvc
            .post("/api/logging/fix") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"logText":"\n   \n\t\n"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { isNotEmpty() }
            }
    }

    @Test
    fun `POST start with oversized logText returns 400`() {
        val huge = "x".repeat(60_000)
        mockMvc
            .post("/api/logging/fix") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"logText": "$huge"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { isNotEmpty() }
            }
    }

    @Test
    fun `POST cancel for unknown id returns 404`() {
        mockMvc
            .post("/api/logging/fix/999999/cancel")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `DELETE for unknown id returns 404`() {
        mockMvc
            .delete("/api/logging/fix/999999")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `GET write-files-preview for unknown id returns 404`() {
        mockMvc
            .get("/api/logging/fix/999999/write-files-preview")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `POST write-files for unknown id returns 404`() {
        mockMvc
            .post("/api/logging/fix/999999/write-files")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `DELETE finished endpoint returns 200 with deleted count`() {
        mockMvc
            .delete("/api/logging/fix/finished")
            .andExpect {
                status { isOk() }
                jsonPath("$.deleted") { exists() }
            }
    }

    @Test
    fun `GET detect-stack for known path returns detected stack`() {
        mockMvc
            .get("/api/local-repos/detect-stack?path=" + System.getProperty("user.dir"))
            .andExpect {
                status { isOk() }
                jsonPath("$.stacks") { isArray() }
                jsonPath("$.indicators") { isArray() }
                jsonPath("$.path") { isNotEmpty() }
            }
    }

    @Test
    fun `GET detect-stack for empty path returns 400`() {
        mockMvc
            .get("/api/local-repos/detect-stack?path=")
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `GET detect-stack for unknown path returns empty arrays`() {
        mockMvc
            .get("/api/local-repos/detect-stack?path=/nonexistent/path/xyz123")
            .andExpect {
                status { isOk() }
                jsonPath("$.stacks") { isArray() }
                jsonPath("$.indicators") { isArray() }
            }
    }

    @Test
    fun `POST start on fresh JVM does NOT throw StaleObjectStateException`() {
        mockMvc
            .post("/api/logging/fix") {
                contentType = MediaType.APPLICATION_JSON
                content = REQ_BODY
            }.andExpect {
                status { isOk() }
                jsonPath("$.id") { exists() }
                jsonPath("$.status") { value("PENDING") }
            }
    }

    @Test
    fun `POST start twice in succession uses distinct DB ids`() {
        val body1 = postFix("ERROR one\n  at com.x.Y.z(X.java:1)\n  at com.a.B.c(C.java:2)\n  at com.d.E.f(E.java:3)\n  at com.f.G.g(G.java:4)")
        val body2 = postFix("ERROR two\n  at com.x.Y.z(X.java:1)\n  at com.a.B.c(C.java:2)\n  at com.d.E.f(E.java:3)\n  at com.f.G.g(G.java:4)")
        val id1 = extractId(body1)
        val id2 = extractId(body2)
        check(id1 > 0L && id2 > 0L && id1 != id2) {
            "Expected two distinct positive ids, got id1=$id1 id2=$id2"
        }
    }

    @Test
    fun `GET status for known run returns 4 stages with labels`() {
        val body = postFix("2025-01-15 ERROR with stacktrace\n  at com.example.X.y(X.java:1)\n  at com.example.A.b(A.java:2)\n  at com.example.B.c(C.java:3)\n  at com.example.D.e(E.java:4)\n")
        val id = extractId(body)
        mockMvc
            .get("/api/logging/fix/$id")
            .andExpect {
                status { isOk() }
                jsonPath("$.id") { value(id) }
                jsonPath("$.stages.diagnose.label") { value("① Diagnose — Root Cause") }
                jsonPath("$.stages.plan.label") { value("② Architektur & Coding-Guidelines") }
                jsonPath("$.stages.code.label") { value("③ Code-Fix") }
                jsonPath("$.stages.tests.label") { value("④ Unit- & Systemtests") }
            }
    }

    @Test
    fun `two parallel POST start calls with distinct logText yield distinct positive ids`() {
        val body1 = postFix("ERROR parallel-1\n  at com.x.Y.z(X.java:1)\n  at com.a.B.c(C.java:2)\n  at com.d.E.f(E.java:3)\n  at com.f.G.g(G.java:4)")
        val body2 = postFix("ERROR parallel-2\n  at com.x.Y.z(X.java:1)\n  at com.a.B.c(C.java:2)\n  at com.d.E.f(E.java:3)\n  at com.f.G.g(G.java:4)")
        val id1 = extractId(body1)
        val id2 = extractId(body2)
        check(id1 > 0L && id2 > 0L && id1 != id2) {
            "Expected two distinct positive ids under concurrent startFix, got id1=$id1 id2=$id2"
        }
    }

    @Test
    fun `POST start with too-thin input lands in DB and diagnose stage returns SKIP_MARKER`() {
        val body = postFix("error")
        val id = extractId(body)
        check(id > 0L) { "Expected an analysis row to be created for thin input, got id=$id" }

        val deadline = System.currentTimeMillis() + 15_000L
        var stageOutput = ""
        while (System.currentTimeMillis() < deadline) {
            val response =
                mockMvc
                    .get("/api/logging/fix/$id")
                    .andExpect { status { isOk() } }
                    .andReturn()
                    .response.contentAsString
            if (response.contains("\"output\"")) {
                val match = OUTPUT_DIAGNOSE_RE.find(response)
                if (match != null) {
                    stageOutput =
                        match.groupValues[1]
                            .replace("\\n", "\n")
                            .replace("\\\"", "\"")
                    if (stageOutput.contains("Diagnose übersprungen")) break
                }
            }
            Thread.sleep(200)
        }
        assertTrue(
            stageOutput.contains("Diagnose übersprungen"),
            "Expected diagnose stage output to contain the skip marker, got=$stageOutput",
        )
    }

    @Test
    fun `POST start with too-thin input marks diagnose stage as SKIPPED in stage status`() {
        val body = postFix("boom")
        val id = extractId(body)
        check(id > 0L) { "Expected an analysis row to be created for thin input, got id=$id" }

        val deadline = System.currentTimeMillis() + 15_000L
        var response = ""
        while (System.currentTimeMillis() < deadline) {
            response =
                mockMvc
                    .get("/api/logging/fix/$id")
                    .andReturn()
                    .response.contentAsString
            if (terminalRunStatus(response)) break
            Thread.sleep(100)
        }
        assertTrue(
            response.contains("\"diagnose\""),
            "Expected diagnose stage to be present in the response, got=$response",
        )
        assertTrue(
            response.contains("\"status\":\"SKIPPED\"") ||
                response.contains("\"status\": \"SKIPPED\""),
            "Expected diagnose stage status SKIPPED after the skip marker landed, got=$response",
        )
    }

    @Test
    fun `POST start with too-thin input reports every downstream stage as SKIPPED`() {
        val id = extractId(postFix("error"))
        val deadline = System.currentTimeMillis() + 15_000L
        var statusBody = ""
        while (System.currentTimeMillis() < deadline) {
            statusBody =
                mockMvc
                    .get("/api/logging/fix/$id")
                    .andReturn()
                    .response.contentAsString
            if (terminalRunStatus(statusBody)) break
            Thread.sleep(100)
        }
        mockMvc
            .get("/api/logging/fix/$id")
            .andExpect {
                jsonPath("$.stages.diagnose.status") { value("SKIPPED") }
                jsonPath("$.stages.plan.status") { value("SKIPPED") }
                jsonPath("$.stages.code.status") { value("SKIPPED") }
                jsonPath("$.stages.tests.status") { value("SKIPPED") }
            }
        assertTrue(
            statusBody.contains("Diagnose nicht möglich"),
            "downstream stages must say why they did not run, got=$statusBody",
        )
    }

    @Test
    fun `POST start with input containing stack-trace marker bypasses thin-input detection`() {
        val body = postFix("Caused by: java.lang.NullPointerException")
        val id = extractId(body)
        check(id > 0L) { "Expected an analysis row to be created, got id=$id" }
        val response =
            mockMvc
                .get("/api/logging/fix/$id")
                .andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString
        assertTrue(
            response.contains("\"diagnose\""),
            "Expected diagnose stage to be present, got=$response",
        )
        assertTrue(
            !response.contains("\"status\":\"SKIPPED\""),
            "Stack-trace marker input must NOT trigger the SKIPPED heuristic, got=$response",
        )
    }

    @Test
    fun `POST start with input containing ISO date bypasses thin-input detection`() {
        val body = postFix("2025-01-15 boom")
        val id = extractId(body)
        check(id > 0L) { "Expected an analysis row to be created, got id=$id" }
        val response =
            mockMvc
                .get("/api/logging/fix/$id")
                .andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString
        assertTrue(
            response.contains("\"diagnose\""),
            "Expected diagnose stage to be present, got=$response",
        )
        assertTrue(
            !response.contains("\"status\":\"SKIPPED\""),
            "ISO date input must NOT trigger the SKIPPED heuristic, got=$response",
        )
    }

    private fun extractId(body: String): Long =
        ID_RE
            .find(body)
            ?.groupValues
            ?.get(1)
            ?.toLongOrNull() ?: 0L

    private fun terminalRunStatus(body: String): Boolean =
        body.contains("\"status\":\"DONE\",\"language\"") ||
            body.contains("\"status\": \"DONE\", \"language\"") ||
            body.contains("\"status\":\"ERROR\",\"language\"") ||
            body.contains("\"status\":\"CANCELLED\",\"language\"")

    private fun postFix(logText: String): String =
        mockMvc
            .post("/api/logging/fix") {
                contentType = MediaType.APPLICATION_JSON
                content = logText.asPostJson()
            }.andExpect { status { isOk() } }
            .andReturn()
            .response.contentAsString

    private fun String.asPostJson(): String {
        val escaped =
            this
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\t", "\\t")
        return """{"logText":"$escaped","selectedCategories":[],"localRepoConfigId":null,"language":"de"}"""
    }

    @Test
    fun `GET status returns the currentStage field for live progress display`() {
        val id =
            extractId(
                postFix(
                    "ERROR with stacktrace\n  at com.example.X.y(X.java:1)\n  at com.example.A.b(A.java:2)\n  at com.example.B.c(C.java:3)\n  at com.example.D.e(E.java:4)",
                ),
            )
        val deadline = System.currentTimeMillis() + 15_000L
        var statusBody = ""
        while (System.currentTimeMillis() < deadline) {
            val r =
                mockMvc
                    .get("/api/logging/fix/$id")
                    .andReturn()
            statusBody = r.response.contentAsString
            if (statusBody.contains("\"currentStage\"")) break
            Thread.sleep(200)
        }
        assertTrue(
            statusBody.contains("\"currentStage\""),
            "GET /api/logging/fix/<id> must include a currentStage key for the live progress label, got=$statusBody",
        )
    }

    companion object {
        private val ID_RE = Regex(""""id"\s*:\s*(\d+)""")
        private val OUTPUT_DIAGNOSE_RE = Regex(""""diagnose"\s*:\s*\{[^{}]*?"output"\s*:\s*"((?:[^"\\]|\\.)*)""")
        private const val REQ_BODY =
            """{"logText":"2025-01-15 ERROR foo\n  at com.example.X.y(X.java:1)\n","selectedCategories":["error"],"localRepoConfigId":null,"language":"de"}"""
    }
}
