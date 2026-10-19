package com.stacklens.adapter.out.gitlab

import com.stacklens.domain.enums.SourceType
import com.stacklens.domain.model.SearchResult

internal object GitlabSearchResultBuilder {
    fun fromBlob(
        apiBase: String,
        project: GitlabProject,
        blob: GitlabBlobResult,
        parsed: ParsedContext,
        sessionId: Long,
        keyword: String,
        crossRefParentId: Long?,
    ): SearchResult =
        SearchResult(
            sessionId = sessionId,
            keyword = keyword,
            sourceType = SourceType.GITLAB,
            resultType = parsed.resultType,
            repoName = project.pathWithNamespace,
            repoUrl = "$apiBase/${project.pathWithNamespace}/-/blob/${blob.ref}/${blob.path}#L${blob.startline}",
            projectId = project.id,
            filePath = blob.path,
            branch = blob.ref,
            lineNumber = blob.startline,
            className = parsed.className,
            functionName = parsed.functionName,
            codeSnippet = blob.data.take(1000),
            crossRefParentId = crossRefParentId,
        )

    fun fromTreeWalk(
        apiBase: String,
        project: GitlabProject,
        branch: String,
        filePath: String,
        lineNumber: Int,
        lineContext: String,
        parsed: ParsedContext,
        sessionId: Long,
        keyword: String,
        crossRefParentId: Long?,
    ): SearchResult =
        SearchResult(
            sessionId = sessionId,
            keyword = keyword,
            sourceType = SourceType.GITLAB,
            resultType = parsed.resultType,
            repoName = project.pathWithNamespace,
            repoUrl = "$apiBase/${project.pathWithNamespace}/-/blob/$branch/$filePath#L$lineNumber",
            projectId = project.id,
            filePath = filePath,
            branch = branch,
            lineNumber = lineNumber,
            className = parsed.className,
            functionName = parsed.functionName,
            codeSnippet = lineContext.take(1000),
            crossRefParentId = crossRefParentId,
        )
}
