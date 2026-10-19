package com.stacklens.application.port.out

import com.stacklens.domain.model.AiAnalysis
import com.stacklens.domain.model.CodeAnalysis
import com.stacklens.domain.model.CodeQaAnalysis
import com.stacklens.domain.model.JiraTicketImplementation
import com.stacklens.domain.model.LoadTestAnalysis
import com.stacklens.domain.model.PipelineAnalysis
import com.stacklens.domain.model.ReviewAnalysis
import com.stacklens.domain.model.ReviewAnalysisConfig
import com.stacklens.domain.model.SecurityAnalysis
import com.stacklens.domain.model.SqlAnalysisRecord

interface AiAnalysisPort {
    fun findTopBySessionIdOrderByCreatedAtDesc(sessionId: Long): AiAnalysis?

    fun findAiAnalysisById(id: Long): AiAnalysis?

    fun saveAiAnalysis(analysis: AiAnalysis): AiAnalysis

    fun findByStatusIn(statuses: List<String>): List<AiAnalysis>

    fun deleteAllAiAnalyses()
}

interface CodeAnalysisPort {
    fun findTopByRepoConfigIdOrderByCreatedAtDesc(repoConfigId: Long): CodeAnalysis?

    fun findTopByGitlabConfigIdAndGitlabProjectIdOrderByCreatedAtDesc(
        gitlabConfigId: Long,
        gitlabProjectId: Long,
    ): CodeAnalysis?

    fun findTopByGithubRepoConfigIdOrderByCreatedAtDesc(githubRepoConfigId: Long): CodeAnalysis?

    fun findCodeAnalysisById(id: Long): CodeAnalysis?

    fun findAllCodeAnalysesOrderByCreatedAtDesc(): List<CodeAnalysis>

    fun findRecentCodeAnalyses(limit: Int): List<CodeAnalysis>

    fun saveCodeAnalysis(analysis: CodeAnalysis): CodeAnalysis

    fun deleteCodeAnalysisById(id: Long)

    fun deleteAllCodeAnalyses()
}

interface ReviewAnalysisPort {
    fun findAllByOrderByCreatedAtDesc(): List<ReviewAnalysis>

    fun findRecentReviewAnalyses(limit: Int): List<ReviewAnalysis>

    fun findReviewAnalysisById(id: Long): ReviewAnalysis?

    fun saveReviewAnalysis(analysis: ReviewAnalysis): ReviewAnalysis

    fun deleteReviewAnalysisById(id: Long)

    fun deleteAllReviewAnalyses()
}

interface ReviewAnalysisConfigPort {
    fun findReviewAnalysisConfig(): ReviewAnalysisConfig?

    fun saveReviewAnalysisConfig(config: ReviewAnalysisConfig): ReviewAnalysisConfig
}

interface SecurityAnalysisPort {
    fun findSecurityAnalysisById(id: Long): SecurityAnalysis?

    fun findAllSecurityAnalysesOrderByCreatedAtDesc(): List<SecurityAnalysis>

    fun findRecentSecurityAnalyses(limit: Int): List<SecurityAnalysis>

    fun saveSecurityAnalysis(analysis: SecurityAnalysis): SecurityAnalysis

    fun deleteSecurityAnalysisById(id: Long)

    fun deleteAllSecurityAnalyses()
}

interface SqlAnalysisPort {
    fun findAllByOrderByCreatedAtDesc(): List<SqlAnalysisRecord>

    fun findRecentSqlAnalyses(limit: Int): List<SqlAnalysisRecord>

    fun findSqlAnalysisById(id: Long): SqlAnalysisRecord?

    fun saveSqlAnalysis(record: SqlAnalysisRecord): SqlAnalysisRecord

    fun deleteSqlAnalysisById(id: Long)

    fun deleteAllSqlAnalyses()
}

interface JwtAnalysisPort {
    fun deleteAllJwtAnalyses()
}

interface PipelineAnalysisPort {
    fun findTopByRepoConfigIdOrderByCreatedAtDesc(repoConfigId: Long): PipelineAnalysis?

    fun findTopByGitlabConfigIdAndGitlabProjectIdOrderByCreatedAtDesc(
        gitlabConfigId: Long,
        gitlabProjectId: Long,
    ): PipelineAnalysis?

    fun findTopByGithubRepoConfigIdOrderByCreatedAtDesc(githubRepoConfigId: Long): PipelineAnalysis?

    fun findPipelineAnalysisById(id: Long): PipelineAnalysis?

    fun findAllPipelineAnalysesOrderByCreatedAtDesc(): List<PipelineAnalysis>

    fun findRecentPipelineAnalyses(limit: Int): List<PipelineAnalysis>

    fun savePipelineAnalysis(analysis: PipelineAnalysis): PipelineAnalysis

    fun deletePipelineAnalysisById(id: Long)

    fun deleteAllPipelineAnalyses()
}

interface LoadTestAnalysisPort {
    fun findTopByGitlabConfigIdAndGitlabProjectIdOrderByCreatedAtDesc(
        gitlabConfigId: Long,
        gitlabProjectId: Long,
    ): LoadTestAnalysis?

    fun findTopByRepoConfigIdOrderByCreatedAtDesc(repoConfigId: Long): LoadTestAnalysis?

    fun findTopByGithubRepoConfigIdOrderByCreatedAtDesc(githubRepoConfigId: Long): LoadTestAnalysis?

    fun findLoadTestAnalysisById(id: Long): LoadTestAnalysis?

    fun findAllLoadTestAnalysesOrderByCreatedAtDesc(): List<LoadTestAnalysis>

    fun findRecentLoadTestAnalyses(limit: Int): List<LoadTestAnalysis>

    fun saveLoadTestAnalysis(analysis: LoadTestAnalysis): LoadTestAnalysis

    fun deleteLoadTestAnalysisById(id: Long)

    fun deleteAllLoadTestAnalyses()
}

interface CodeQaAnalysisPort {
    fun findAllByOrderByCreatedAtDesc(): List<CodeQaAnalysis>

    fun findRecent(limit: Int): List<CodeQaAnalysis>

    fun findCodeQaAnalysisById(id: Long): CodeQaAnalysis?

    fun saveCodeQaAnalysis(analysis: CodeQaAnalysis): CodeQaAnalysis

    fun deleteCodeQaAnalysisById(id: Long)

    fun deleteAllCodeQaAnalyses()
}

interface JiraTicketImplementationPort {
    fun findAllByOrderByCreatedAtDesc(): List<JiraTicketImplementation>

    fun findRecentJiraTicketImplementations(limit: Int): List<JiraTicketImplementation>

    fun findJiraTicketImplementationById(id: Long): JiraTicketImplementation?

    fun saveJiraTicketImplementation(analysis: JiraTicketImplementation): JiraTicketImplementation

    fun deleteJiraTicketImplementationById(id: Long)

    fun deleteFinishedAnalyses(): Int

    fun deleteAllJiraTicketImplementations()
}
