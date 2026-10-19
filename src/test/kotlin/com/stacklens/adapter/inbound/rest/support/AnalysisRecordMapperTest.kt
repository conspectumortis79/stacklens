package com.stacklens.adapter.inbound.rest.support

import com.stacklens.domain.model.CodeAnalysis
import com.stacklens.domain.model.LoadTestAnalysis
import com.stacklens.domain.model.PipelineAnalysis
import com.stacklens.domain.model.SecurityAnalysis
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class AnalysisRecordMapperTest {
    @Test
    fun `CodeAnalysis response map returns default values`() {
        val map = CodeAnalysis().toResponseMap()

        assertEquals(0L, map["id"])
        assertNull(map["repoConfigId"])
        assertNull(map["gitlabConfigId"])
        assertNull(map["gitlabProjectId"])
        assertNull(map["gitlabProjectPath"])
        assertEquals("PENDING", map["status"])
        assertNull(map["summaryText"])
        assertNull(map["findingsJson"])
        assertNull(map["fileChangesJson"])
        assertNull(map["errorMessage"])
        assertNull(map["modelUsed"])
        assertNull(map["mrUrl"])
        assertNull(map["mrBranch"])
        assertNull(map["mermaid"])
        assertNull(map["drawioXml"])
        assertNull(map["monorepoSubPath"])
        assertNull(map["monorepoAppName"])
        assertNull(map["githubRepoConfigId"])
        assertNull(map["completedAt"])
        assertNotNull(map["createdAt"])
    }

    @Test
    fun `CodeAnalysis response map serializes timestamps`() {
        val created = LocalDateTime.of(2024, 3, 15, 10, 30, 0)
        val completed = LocalDateTime.of(2024, 3, 15, 11, 0, 0)
        val analysis = CodeAnalysis(createdAt = created, completedAt = completed)

        val map = analysis.toResponseMap()

        assertEquals(created.toString(), map["createdAt"])
        assertEquals(completed.toString(), map["completedAt"])
    }

    @Test
    fun `CodeAnalysis response map contains all populated fields`() {
        val created = LocalDateTime.of(2024, 5, 1, 8, 0)
        val completed = LocalDateTime.of(2024, 5, 1, 9, 0)

        val analysis =
            CodeAnalysis(
                id = 42L,
                repoConfigId = 10L,
                gitlabConfigId = 11L,
                gitlabProjectId = 12L,
                gitlabProjectPath = "ns/project",
                status = "COMPLETED",
                summaryText = "Looks good",
                findingsJson = """[{"id":1}]""",
                fileChangesJson = """[{"path":"File.kt"}]""",
                errorMessage = null,
                modelUsed = "claude-3",
                mrUrl = "https://gitlab.com/mr/1",
                mrBranch = "fix/123",
                mermaid = "graph TD;",
                drawioXml = "<mxfile/>",
                monorepoSubPath = "services/backend",
                monorepoAppName = "backend",
                createdAt = created,
                completedAt = completed,
            )

        val map = analysis.toResponseMap()
        assertEquals(42L, map["id"])
        assertEquals(10L, map["repoConfigId"])
        assertEquals(11L, map["gitlabConfigId"])
        assertEquals(12L, map["gitlabProjectId"])
        assertEquals("ns/project", map["gitlabProjectPath"])
        assertEquals("COMPLETED", map["status"])
        assertEquals("Looks good", map["summaryText"])
        assertEquals("""[{"id":1}]""", map["findingsJson"])
        assertEquals("""[{"path":"File.kt"}]""", map["fileChangesJson"])
        assertNull(map["errorMessage"])
        assertEquals("claude-3", map["modelUsed"])
        assertEquals("https://gitlab.com/mr/1", map["mrUrl"])
        assertEquals("fix/123", map["mrBranch"])
        assertEquals("graph TD;", map["mermaid"])
        assertEquals("<mxfile/>", map["drawioXml"])
        assertEquals("services/backend", map["monorepoSubPath"])
        assertEquals("backend", map["monorepoAppName"])
        assertEquals(created.toString(), map["createdAt"])
        assertEquals(completed.toString(), map["completedAt"])
    }

    @Test
    fun `CodeAnalysis response map contains exactly the expected keys`() {
        val expectedKeys =
            setOf(
                "id",
                "status",
                "summaryText",
                "findingsJson",
                "fileChangesJson",
                "errorMessage",
                "modelUsed",
                "mrUrl",
                "mrBranch",
                "mermaid",
                "drawioXml",
                "monorepoSubPath",
                "monorepoAppName",
                "createdAt",
                "completedAt",
                "repoConfigId",
                "gitlabConfigId",
                "gitlabProjectId",
                "gitlabProjectPath",
                "githubRepoConfigId",
            )
        assertEquals(expectedKeys, CodeAnalysis().toResponseMap().keys)
    }

    @Test
    fun `SecurityAnalysis response map with default values`() {
        val map = SecurityAnalysis().toResponseMap()
        assertEquals(0L, map["id"])
        assertEquals("PENDING", map["status"])
        assertEquals(0, map["currentIteration"])
        assertEquals(1, map["totalIterations"])
        assertNull(map["summaryText"])
        assertNull(map["findingsJson"])
    }

    @Test
    fun `SecurityAnalysis response map includes all expected keys`() {
        val a = SecurityAnalysis(id = 5L, status = "RUNNING")
        val m = a.toResponseMap()
        assertEquals(5L, m["id"])
        assertEquals("RUNNING", m["status"])
        assertNull(m["repoConfigId"])
        assertNull(m["gitlabConfigId"])
        assertNull(m["gitlabProjectId"])
        assertNull(m["gitlabProjectPath"])
        assertNull(m["githubRepoConfigId"])
        assertNull(m["summaryText"])
        assertNull(m["findingsJson"])
        assertNull(m["errorMessage"])
        assertNull(m["modelUsed"])
        assertEquals(0, m["currentIteration"])
        assertEquals(1, m["totalIterations"])
        assertNull(m["currentStep"])
        assertNotNull(m["createdAt"])
        assertNull(m["completedAt"])
        assertEquals(16, m.size)
    }

    @Test
    fun `SecurityAnalysis response map keeps raw timestamps`() {
        val created = LocalDateTime.of(2024, 1, 1, 12, 0)
        val completed = LocalDateTime.of(2024, 1, 1, 13, 0)
        val map = SecurityAnalysis(createdAt = created, completedAt = completed).toResponseMap()
        assertEquals(created, map["createdAt"])
        assertEquals(completed, map["completedAt"])
    }

    @Test
    fun `LoadTestAnalysis response map returns expected default values`() {
        val map = LoadTestAnalysis().toResponseMap()
        assertEquals(0L, map["id"])
        assertEquals("PENDING", map["status"])
        assertNull(map["summaryText"])
        assertNull(map["findingsJson"])
        assertNull(map["k6Script"])
        assertNull(map["environmentsJson"])
        assertNull(map["errorMessage"])
        assertNull(map["modelUsed"])
        assertNull(map["k6Output"])
        assertNull(map["k6Error"])
        assertNull(map["k6ExitCode"])
        assertNull(map["k6SummaryJson"])
    }

    @Test
    fun `LoadTestAnalysis response map with all populated fields`() {
        val a =
            LoadTestAnalysis(
                id = 1L,
                gitlabConfigId = 10L,
                gitlabProjectId = 11L,
                gitlabProjectPath = "g/p",
                repoConfigId = 12L,
                githubRepoConfigId = 13L,
                status = "COMPLETED",
                summaryText = "s",
                findingsJson = "[{}]",
                k6Script = "import http from 'k6/http'",
                environmentsJson = "[]",
                errorMessage = null,
                modelUsed = "gpt-4",
                k6Output = "stdout",
                k6Error = "stderr",
                k6ExitCode = 0,
                k6SummaryJson = "{}",
            )
        val map = a.toResponseMap()
        assertEquals(1L, map["id"])
        assertEquals(10L, map["gitlabConfigId"])
        assertEquals(11L, map["gitlabProjectId"])
        assertEquals("g/p", map["gitlabProjectPath"])
        assertEquals(12L, map["repoConfigId"])
        assertEquals(13L, map["githubRepoConfigId"])
        assertEquals("COMPLETED", map["status"])
        assertEquals("s", map["summaryText"])
        assertEquals("[{}]", map["findingsJson"])
        assertEquals("import http from 'k6/http'", map["k6Script"])
        assertEquals("[]", map["environmentsJson"])
        assertEquals("gpt-4", map["modelUsed"])
        assertEquals("stdout", map["k6Output"])
        assertEquals("stderr", map["k6Error"])
        assertEquals(0, map["k6ExitCode"])
        assertEquals("{}", map["k6SummaryJson"])
    }

    @Test
    fun `LoadTestAnalysis response map serializes completedAt only when set`() {
        val now = LocalDateTime.of(2024, 6, 1, 12, 0)
        assertNull(LoadTestAnalysis().toResponseMap()["completedAt"])
        assertEquals(now.toString(), LoadTestAnalysis(completedAt = now).toResponseMap()["completedAt"])
    }

    @Test
    fun `LoadTestAnalysis response map includes all expected keys`() {
        val a = LoadTestAnalysis(id = 5L, status = "RUNNING")
        val m = a.toResponseMap()
        assertEquals(5L, m["id"])
        assertEquals("RUNNING", m["status"])
        assertNull(m["summaryText"])
        assertNull(m["findingsJson"])
        assertNull(m["k6Script"])
        assertNull(m["environmentsJson"])
        assertNull(m["errorMessage"])
        assertNull(m["modelUsed"])
        assertNull(m["gitlabConfigId"])
        assertNull(m["gitlabProjectId"])
        assertNull(m["gitlabProjectPath"])
        assertNull(m["repoConfigId"])
        assertNull(m["githubRepoConfigId"])
        assertNotNull(m["createdAt"])
        assertNull(m["completedAt"])
        assertNull(m["k6Output"])
        assertNull(m["k6Error"])
        assertNull(m["k6ExitCode"])
        assertNull(m["k6SummaryJson"])
        assertEquals(19, m.size)
    }

    @Test
    fun `PipelineAnalysis response map returns the expected default values`() {
        val map = PipelineAnalysis().toResponseMap()
        assertEquals(0L, map["id"])
        assertNotNull(map["createdAt"])
        assertEquals(null, map["repoConfigId"])
        assertEquals(null, map["gitlabConfigId"])
        assertEquals(null, map["gitlabProjectId"])
        assertEquals(null, map["gitlabProjectPath"])
        assertEquals(null, map["githubRepoConfigId"])
        assertEquals("PENDING", map["status"])
        assertEquals(null, map["summaryText"])
        assertEquals(null, map["findingsJson"])
        assertEquals(null, map["fileDescriptionsJson"])
        assertEquals(null, map["optimizationsJson"])
        assertEquals(null, map["mermaid"])
        assertEquals(null, map["errorMessage"])
        assertEquals(null, map["modelUsed"])
        assertEquals(null, map["completedAt"])
        assertEquals(null, map["userPromptAdditions"])
        assertEquals(null, map["mrUrl"])
        assertEquals(null, map["mrBranch"])
        assertEquals(null, map["mrProvider"])
    }

    @Test
    fun `PipelineAnalysis response map with all populated fields`() {
        val now = LocalDateTime.of(2024, 1, 2, 3, 4, 5)
        val completed = LocalDateTime.of(2024, 1, 2, 4, 0, 0)
        val analysis =
            PipelineAnalysis(
                id = 1L,
                repoConfigId = 10L,
                gitlabConfigId = 11L,
                gitlabProjectId = 12L,
                gitlabProjectPath = "ns/proj",
                status = "COMPLETED",
                summaryText = "All good",
                findingsJson = "[{}]",
                errorMessage = null,
                modelUsed = "gpt-4",
                mrUrl = "https://gitlab.com/mr/1",
                mrBranch = "main",
                createdAt = now,
                completedAt = completed,
            )
        val map = analysis.toResponseMap()
        assertEquals(1L, map["id"])
        assertEquals(10L, map["repoConfigId"])
        assertEquals(11L, map["gitlabConfigId"])
        assertEquals(12L, map["gitlabProjectId"])
        assertEquals("ns/proj", map["gitlabProjectPath"])
        assertEquals("COMPLETED", map["status"])
        assertEquals("All good", map["summaryText"])
        assertEquals("[{}]", map["findingsJson"])
        assertEquals(null, map["errorMessage"])
        assertEquals("gpt-4", map["modelUsed"])
        assertEquals("https://gitlab.com/mr/1", map["mrUrl"])
        assertEquals("main", map["mrBranch"])
        assertEquals(now.toString(), map["createdAt"])
        assertEquals(completed.toString(), map["completedAt"])
    }

    @Test
    fun `PipelineAnalysis response map reflects mutable updates and null values`() {
        val p = PipelineAnalysis()
        p.status = "RUNNING"
        p.summaryText = "in progress"
        val map = p.toResponseMap()
        assertEquals("RUNNING", map["status"])
        assertEquals("in progress", map["summaryText"])
        assertTrue(map.containsKey("mrUrl"))
        assertNull(map["mrUrl"])
    }

    @Test
    fun `PipelineAnalysis response map contains all expected keys and timestamp branches`() {
        val expectedKeys =
            setOf(
                "id",
                "status",
                "summaryText",
                "findingsJson",
                "fileDescriptionsJson",
                "optimizationsJson",
                "mermaid",
                "errorMessage",
                "modelUsed",
                "createdAt",
                "completedAt",
                "repoConfigId",
                "gitlabConfigId",
                "gitlabProjectId",
                "gitlabProjectPath",
                "githubRepoConfigId",
                "userPromptAdditions",
                "mrUrl",
                "mrBranch",
                "mrProvider",
            )
        assertEquals(expectedKeys, PipelineAnalysis().toResponseMap().keys)
        assertNull(PipelineAnalysis(completedAt = null).toResponseMap()["completedAt"])
        val now = LocalDateTime.of(2024, 1, 1, 12, 0, 0)
        assertEquals(now.toString(), PipelineAnalysis(completedAt = now).toResponseMap()["completedAt"])
    }

    @Test
    fun `PipelineAnalysis response map contains all populated values`() {
        val a =
            PipelineAnalysis(
                id = 42,
                repoConfigId = 1L,
                gitlabConfigId = 2L,
                gitlabProjectId = 3L,
                gitlabProjectPath = "group/proj",
                githubRepoConfigId = 4L,
                status = "COMPLETED",
                summaryText = "summary",
                findingsJson = "[]",
                fileDescriptionsJson = "[]",
                optimizationsJson = "[]",
                mermaid = "graph TD",
                errorMessage = "err",
                modelUsed = "gpt-4",
                userPromptAdditions = "extra",
                mrUrl = "https://example.com/mr/1",
                mrBranch = "branch-1",
                mrProvider = "GITLAB",
            )
        val map = a.toResponseMap()
        assertEquals(42L, map["id"])
        assertEquals("COMPLETED", map["status"])
        assertEquals("summary", map["summaryText"])
        assertEquals("[]", map["findingsJson"])
        assertEquals("[]", map["fileDescriptionsJson"])
        assertEquals("[]", map["optimizationsJson"])
        assertEquals("graph TD", map["mermaid"])
        assertEquals("err", map["errorMessage"])
        assertEquals("gpt-4", map["modelUsed"])
        assertEquals(1L, map["repoConfigId"])
        assertEquals(2L, map["gitlabConfigId"])
        assertEquals(3L, map["gitlabProjectId"])
        assertEquals("group/proj", map["gitlabProjectPath"])
        assertEquals(4L, map["githubRepoConfigId"])
        assertEquals("extra", map["userPromptAdditions"])
        assertEquals("https://example.com/mr/1", map["mrUrl"])
        assertEquals("branch-1", map["mrBranch"])
        assertEquals("GITLAB", map["mrProvider"])
    }
}
