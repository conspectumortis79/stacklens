package com.stacklens.adapter.out.gitlab

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GitlabContextAndPipelineModelsTest {
    @Test
    fun `ContextProject default constructor`() {
        val p = ContextMapAdapter.ContextProject()
        assertEquals(0, p.id)
        assertEquals("", p.name)
        assertEquals("", p.pathWithNamespace)
        assertEquals("main", p.defaultBranch)
    }

    @Test
    fun `BlobResult default constructor`() {
        val b = ContextMapAdapter.BlobResult()
        assertEquals(0, b.projectId)
        assertEquals("", b.path)
        assertEquals("", b.ref)
        assertEquals(0, b.startline)
        assertEquals("", b.data)
    }

    @Test
    fun `TreeEntry default constructor`() {
        val t = ContextMapAdapter.TreeEntry()
        assertEquals("", t.id)
        assertEquals("", t.name)
        assertEquals("", t.path)
        assertEquals("", t.type)
    }

    @Test
    fun `GitlabProject with required values and default branch`() {
        val p =
            PipelineSearchAdapter.GitlabProject(
                id = 42,
                name = "repo",
                pathWithNamespace = "grp/repo",
                webUrl = "https://gitlab.com/grp/repo",
            )
        assertEquals(42, p.id)
        assertEquals("repo", p.name)
        assertEquals("grp/repo", p.pathWithNamespace)
        assertEquals("https://gitlab.com/grp/repo", p.webUrl)
        assertEquals("main", p.defaultBranch)
    }

    @Test
    fun `GitlabBlobResult default constructor`() {
        val b = PipelineSearchAdapter.GitlabBlobResult()
        assertEquals(0, b.projectId)
        assertEquals("", b.path)
        assertEquals("", b.ref)
        assertEquals(0, b.startline)
        assertEquals("", b.data)
    }

    @Test
    fun `TreeItem default constructor`() {
        val t = PipelineSearchAdapter.TreeItem()
        assertEquals("", t.id)
        assertEquals("", t.name)
        assertEquals("", t.type)
        assertEquals("", t.path)
    }

    @Test
    fun `ParsedPipeline holds values`() {
        val p =
            PipelineSearchAdapter.ParsedPipeline(
                jobName = "build",
                stageName = "test",
                resultType = com.stacklens.domain.enums.ResultType.PIPELINE_JOB,
            )
        assertEquals("build", p.jobName)
        assertEquals("test", p.stageName)
        assertEquals(com.stacklens.domain.enums.ResultType.PIPELINE_JOB, p.resultType)
    }
}
