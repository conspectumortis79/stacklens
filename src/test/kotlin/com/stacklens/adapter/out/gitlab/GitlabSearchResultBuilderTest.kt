package com.stacklens.adapter.out.gitlab

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.stacklens.domain.enums.ResultType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@JsonIgnoreProperties(ignoreUnknown = true)
private data class TestProject(
    val id: Long,
    @JsonProperty("path_with_namespace") val pathWithNamespace: String,
    @JsonProperty("default_branch") val defaultBranch: String? = "main",
)

private fun project(
    id: Long = 42L,
    path: String = "group/sub/project",
    branch: String = "main",
): GitlabProject =
    GitlabProject(
        id = id,
        name = path.substringAfterLast('/'),
        pathWithNamespace = path,
        defaultBranch = branch,
    )

private fun parsed(
    className: String? = null,
    functionName: String? = null,
    resultType: ResultType = ResultType.FILE,
): ParsedContext = ParsedContext(className, functionName, resultType)

private fun blob(
    ref: String = "main",
    path: String = "src/Foo.kt",
    startline: Int = 7,
    data: String = "data",
): GitlabBlobResult =
    GitlabBlobResult(
        projectId = 42L,
        path = path,
        ref = ref,
        startline = startline,
        data = data,
    )

class GitlabSearchResultBuilderTest {
    @Test
    fun `fromBlob builds a SearchResult anchored to the blob startline`() {
        val p = project()
        val b = blob(ref = "develop", path = "src/Foo.kt", startline = 17, data = "long data…")
        val result =
            GitlabSearchResultBuilder.fromBlob(
                apiBase = "https://gitlab.example.com",
                project = p,
                blob = b,
                parsed = parsed(className = "Foo", functionName = "bar", resultType = ResultType.FUNCTION),
                sessionId = 99L,
                keyword = "kw",
                crossRefParentId = 5L,
            )
        assertEquals(99L, result.sessionId)
        assertEquals("kw", result.keyword)
        assertEquals("group/sub/project", result.repoName)
        assertEquals(
            "https://gitlab.example.com/group/sub/project/-/blob/develop/src/Foo.kt#L17",
            result.repoUrl,
        )
        assertEquals(42L, result.projectId)
        assertEquals("src/Foo.kt", result.filePath)
        assertEquals("develop", result.branch)
        assertEquals(17, result.lineNumber)
        assertEquals("Foo", result.className)
        assertEquals("bar", result.functionName)
        assertEquals("long data…", result.codeSnippet)
        assertEquals(5L, result.crossRefParentId)
    }

    @Test
    fun `fromBlob truncates codeSnippet to 1000 chars`() {
        val b = blob(data = "x".repeat(1500))
        val result =
            GitlabSearchResultBuilder.fromBlob(
                apiBase = "https://gitlab.example.com",
                project = project(),
                blob = b,
                parsed = parsed(),
                sessionId = 1L,
                keyword = "kw",
                crossRefParentId = null,
            )
        assertEquals(1000, result.codeSnippet?.length)
    }

    @Test
    fun `fromTreeWalk builds a SearchResult anchored to the matched line`() {
        val p = project(branch = "develop")
        val result =
            GitlabSearchResultBuilder.fromTreeWalk(
                apiBase = "https://gitlab.example.com",
                project = p,
                branch = "develop",
                filePath = "src/Foo.kt",
                lineNumber = 42,
                lineContext = "ctx",
                parsed = parsed(className = "Foo", functionName = null, resultType = ResultType.CLASS),
                sessionId = 1L,
                keyword = "kw",
                crossRefParentId = null,
            )
        assertEquals("develop", result.branch)
        assertEquals("src/Foo.kt", result.filePath)
        assertEquals(42, result.lineNumber)
        assertEquals("Foo", result.className)
        assertNull(result.functionName)
        assertEquals(ResultType.CLASS, result.resultType)
        assertEquals(
            "https://gitlab.example.com/group/sub/project/-/blob/develop/src/Foo.kt#L42",
            result.repoUrl,
        )
        assertNull(result.crossRefParentId)
    }

    @Test
    fun `fromTreeWalk truncates codeSnippet to 1000 chars`() {
        val result =
            GitlabSearchResultBuilder.fromTreeWalk(
                apiBase = "https://gitlab.example.com",
                project = project(),
                branch = "main",
                filePath = "src/Foo.kt",
                lineNumber = 1,
                lineContext = "y".repeat(1500),
                parsed = parsed(),
                sessionId = 1L,
                keyword = "kw",
                crossRefParentId = null,
            )
        assertEquals(1000, result.codeSnippet?.length)
    }
}
