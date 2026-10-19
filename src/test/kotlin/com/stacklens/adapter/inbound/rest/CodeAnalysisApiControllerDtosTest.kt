package com.stacklens.adapter.inbound.rest

import com.stacklens.adapter.inbound.rest.CodeAnalysisApiController.CreateMrDto
import com.stacklens.adapter.inbound.rest.CodeAnalysisApiController.SaveDiagramRequest
import com.stacklens.adapter.inbound.rest.CodeAnalysisApiController.StartGithubDto
import com.stacklens.adapter.inbound.rest.CodeAnalysisApiController.StartGitlabDto
import com.stacklens.adapter.inbound.rest.CodeAnalysisApiController.StartLocalDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CodeAnalysisApiControllerDtosTest {
    @Test
    fun `StartLocalDto holds repoConfigId`() {
        val dto = StartLocalDto(repoConfigId = 7L)
        assertEquals(7L, dto.repoConfigId)
    }

    @Test
    fun `StartGitlabDto holds all three fields`() {
        val dto = StartGitlabDto(gitlabConfigId = 1L, projectId = 2L, projectPath = "ns/proj")
        assertEquals(1L, dto.gitlabConfigId)
        assertEquals(2L, dto.projectId)
        assertEquals("ns/proj", dto.projectPath)
    }

    @Test
    fun `StartGithubDto holds githubRepoConfigId`() {
        val dto = StartGithubDto(githubRepoConfigId = 42L)
        assertEquals(42L, dto.githubRepoConfigId)
    }

    @Test
    fun `CreateMrDto defaults both fields to null`() {
        val dto = CreateMrDto()
        assertNull(dto.gitlabConfigId)
        assertNull(dto.projectId)
    }

    @Test
    fun `CreateMrDto holds populated fields`() {
        val dto = CreateMrDto(gitlabConfigId = 3L, projectId = 9L)
        assertEquals(3L, dto.gitlabConfigId)
        assertEquals(9L, dto.projectId)
    }

    @Test
    fun `CreateMrDto copy preserves untouched fields`() {
        val original = CreateMrDto(gitlabConfigId = 3L, projectId = 9L)
        val modified = original.copy(projectId = 99L)
        assertEquals(3L, modified.gitlabConfigId)
        assertEquals(99L, modified.projectId)
    }

    @Test
    fun `SaveDiagramRequest holds path filename and content`() {
        val dto = SaveDiagramRequest(path = "/tmp", filename = "f.drawio", content = "x")
        assertEquals("/tmp", dto.path)
        assertEquals("f.drawio", dto.filename)
        assertEquals("x", dto.content)
    }

    @Test
    fun `SaveDiagramRequest equality holds by field value`() {
        val a = SaveDiagramRequest(path = "p", filename = "f", content = "c")
        val b = SaveDiagramRequest(path = "p", filename = "f", content = "c")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
