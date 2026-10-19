package com.stacklens.adapter.out.persistence

import com.stacklens.application.port.out.CodeAnalysisPort
import com.stacklens.domain.model.CodeAnalysis
import org.springframework.data.domain.PageRequest
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface CodeAnalysisRepository :
    JpaRepository<CodeAnalysis, Long>,
    CodeAnalysisPort {
    override fun findTopByRepoConfigIdOrderByCreatedAtDesc(repoConfigId: Long): CodeAnalysis?

    override fun findTopByGitlabConfigIdAndGitlabProjectIdOrderByCreatedAtDesc(
        gitlabConfigId: Long,
        gitlabProjectId: Long,
    ): CodeAnalysis?

    override fun findTopByGithubRepoConfigIdOrderByCreatedAtDesc(githubRepoConfigId: Long): CodeAnalysis?

    @Query("SELECT e FROM CodeAnalysis e WHERE e.id = :id")
    override fun findCodeAnalysisById(
        @Param("id") id: Long,
    ): CodeAnalysis?

    override fun findAllCodeAnalysesOrderByCreatedAtDesc(): List<CodeAnalysis> =
        findAll(
            org.springframework.data.domain.Sort
                .by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"),
        )

    override fun findRecentCodeAnalyses(limit: Int): List<CodeAnalysis> =
        findAll(
            PageRequest.of(
                0,
                limit,
                org.springframework.data.domain.Sort
                    .by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"),
            ),
        ).content

    override fun saveCodeAnalysis(analysis: CodeAnalysis): CodeAnalysis = save(analysis)

    override fun deleteCodeAnalysisById(id: Long) = deleteById(id)

    override fun deleteAllCodeAnalyses() = deleteAll()
}
