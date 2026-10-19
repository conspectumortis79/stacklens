package com.stacklens.application.service.codeanalysis
import com.stacklens.adapter.out.analysis.CodeAnalysisRepoApi
import com.stacklens.application.port.out.analysis.CodeAnalysisRepoData
import com.stacklens.domain.model.CodeAnalysis
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class LocalCodeAnalysisStrategyTest {
    private fun portReturning(
        result: CodeAnalysisRepoData? = null,
        onLoad: (Long) -> Unit = {},
    ): CodeAnalysisRepoApi =
        object : CodeAnalysisRepoApi {
            override fun loadLocal(repoConfigId: Long): CodeAnalysisRepoData? {
                onLoad(repoConfigId)
                return result
            }

            override fun listGitlabFilePaths(
                gitlabConfigId: Long,
                projectId: Long,
            ): List<String> = emptyList()

            override fun loadGitlab(
                gitlabConfigId: Long,
                projectId: Long,
                projectPath: String,
                subPath: String,
            ): CodeAnalysisRepoData? = null

            override fun loadGithub(githubRepoConfigId: Long): CodeAnalysisRepoData = throw UnsupportedOperationException()

            override fun findGitlabConfig(gitlabConfigId: Long) = null
        }

    @Test
    fun `LocalCodeAnalysisStrategy params with null analysis has null repoConfigId`() {
        val port = portReturning()
        val strategy = LocalCodeAnalysisStrategy(port)
        val params = strategy.params(null)
        assertEquals(null, params.repoConfigId)
    }

    @Test
    fun `LocalCodeAnalysisStrategy params with populated analysis uses its repoConfigId`() {
        val port = portReturning()
        val strategy = LocalCodeAnalysisStrategy(port)
        val analysis = CodeAnalysis(repoConfigId = 7L)
        val params = strategy.params(analysis)
        assertEquals(7L, params.repoConfigId)
    }

    @Test
    fun `LocalCodeAnalysisStrategy load delegates to port loadLocal with configId`() {
        var captured: Long? = null
        val port =
            portReturning(
                onLoad = { captured = it },
            )
        val strategy = LocalCodeAnalysisStrategy(port)
        strategy.load(CodeAnalysisLoadParams(repoConfigId = 42L))
        assertEquals(42L, captured)
    }

    @Test
    fun `LocalCodeAnalysisStrategy load returns port result when not null`() {
        val repoData =
            CodeAnalysisRepoData(
                appName = "my-app",
                repoPath = "/tmp/repo",
                tree = "src/Foo.kt",
                contents = "val foo = 1",
            )
        val port = portReturning(result = repoData)
        val strategy = LocalCodeAnalysisStrategy(port)
        val result = strategy.load(CodeAnalysisLoadParams(repoConfigId = 1L))
        assertNotNull(result)
        assertEquals("my-app", result?.appName)
        assertEquals("/tmp/repo", result?.repoPath)
    }

    @Test
    fun `LocalCodeAnalysisStrategy load returns null when port returns null`() {
        val port = portReturning(result = null)
        val strategy = LocalCodeAnalysisStrategy(port)
        val result = strategy.load(CodeAnalysisLoadParams(repoConfigId = 1L))
        assertEquals(null, result)
    }

    @Test
    fun `LocalCodeAnalysisStrategy load returns null when repoConfigId is null`() {
        var captured: Long? = null
        val port =
            portReturning(
                onLoad = { captured = it },
            )
        val strategy = LocalCodeAnalysisStrategy(port)
        assertEquals(null, strategy.load(CodeAnalysisLoadParams(repoConfigId = null)))
        assertEquals(null, captured)
    }
}
