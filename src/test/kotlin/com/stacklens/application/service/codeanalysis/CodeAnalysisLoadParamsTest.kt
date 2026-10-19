package com.stacklens.application.service.codeanalysis

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CodeAnalysisLoadParamsTest {
    @Test
    fun `appName uses gitlabProjectPath when provided`() {
        val p = CodeAnalysisLoadParams(gitlabProjectPath = "group/project")
        assertEquals("group/project", p.appName)
    }

    @Test
    fun `appName appends subPath to gitlabProjectPath when both provided`() {
        val p =
            CodeAnalysisLoadParams(
                gitlabProjectPath = "group/project",
                subPath = "subdir",
            )
        assertEquals("group/project/subdir", p.appName)
    }

    @Test
    fun `appName uses subPath when no gitlabProjectPath but subPath provided`() {
        val p = CodeAnalysisLoadParams(subPath = "subsubdir")
        assertEquals("subsubdir", p.appName)
    }

    @Test
    fun `appName uses local prefix when only repoConfigId provided`() {
        val p = CodeAnalysisLoadParams(repoConfigId = 42L)
        assertEquals("local-42", p.appName)
    }

    @Test
    fun `appName uses github prefix when only githubRepoConfigId provided`() {
        val p = CodeAnalysisLoadParams(githubRepoConfigId = 7L)
        assertEquals("github-7", p.appName)
    }

    @Test
    fun `appName returns unknown when nothing is provided`() {
        val p = CodeAnalysisLoadParams()
        assertEquals("unknown", p.appName)
    }

    @Test
    fun `appName ignores blank subPath when gitlabProjectPath is provided`() {
        val p =
            CodeAnalysisLoadParams(
                gitlabProjectPath = "group/project",
                subPath = "   ",
            )
        assertEquals("group/project", p.appName)
    }

    @Test
    fun `appName prefers gitlabProjectPath over repoConfigId`() {
        val p =
            CodeAnalysisLoadParams(
                gitlabProjectPath = "group/project",
                repoConfigId = 42L,
            )
        assertEquals("group/project", p.appName)
    }
}
