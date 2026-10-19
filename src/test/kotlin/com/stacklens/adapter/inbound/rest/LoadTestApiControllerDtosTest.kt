package com.stacklens.adapter.inbound.rest

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LoadTestApiControllerDtosTest {
    @Test
    fun `StartLoadTestDto holds all three positional fields`() {
        val dto = StartLoadTestDto(gitlabConfigId = 7L, projectId = 42L, projectPath = "group/project")
        assertEquals(7L, dto.gitlabConfigId)
        assertEquals(42L, dto.projectId)
        assertEquals("group/project", dto.projectPath)
    }

    @Test
    fun `StartLoadTestDto is immutable`() {
        val dto = StartLoadTestDto(gitlabConfigId = 1L, projectId = 2L, projectPath = "p")
        val copy = dto.copy(projectId = 99L)
        assertEquals(99L, copy.projectId)
        assertEquals(1L, copy.gitlabConfigId)
    }

    @Test
    fun `StartLocalLoadTestDto holds repoConfigId`() {
        val dto = StartLocalLoadTestDto(repoConfigId = 13L)
        assertEquals(13L, dto.repoConfigId)
    }

    @Test
    fun `StartGithubLoadTestDto holds githubRepoConfigId`() {
        val dto = StartGithubLoadTestDto(githubRepoConfigId = 99L)
        assertEquals(99L, dto.githubRepoConfigId)
    }

    @Test
    fun `RunTestDto default values are null`() {
        val dto = RunTestDto()
        assertNull(dto.bearerToken)
        assertNull(dto.environmentName)
        assertNull(dto.customScript)
    }

    @Test
    fun `RunTestDto holds all fields when populated`() {
        val dto =
            RunTestDto(
                bearerToken = "token-abc",
                environmentName = "staging",
                customScript = "import http from 'k6/http'\n\nexport default function() {}",
            )
        assertEquals("token-abc", dto.bearerToken)
        assertEquals("staging", dto.environmentName)
        assertEquals("import http from 'k6/http'\n\nexport default function() {}", dto.customScript)
    }

    @Test
    fun `RunTestDto copy allows partial overrides`() {
        val original = RunTestDto(bearerToken = "t1", environmentName = "env1", customScript = "s1")
        val modified = original.copy(environmentName = "env2")
        assertEquals("t1", modified.bearerToken)
        assertEquals("env2", modified.environmentName)
        assertEquals("s1", modified.customScript)
    }

    @Test
    fun `RunTestDto equality holds by field value`() {
        val a = RunTestDto(bearerToken = "t", environmentName = "e", customScript = "s")
        val b = RunTestDto(bearerToken = "t", environmentName = "e", customScript = "s")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
