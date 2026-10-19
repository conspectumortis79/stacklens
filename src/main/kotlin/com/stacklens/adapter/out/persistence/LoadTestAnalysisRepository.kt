package com.stacklens.adapter.out.persistence

import com.stacklens.application.port.out.LoadTestAnalysisPort
import com.stacklens.domain.model.LoadTestAnalysis
import org.springframework.data.domain.PageRequest
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface LoadTestAnalysisRepository :
    JpaRepository<LoadTestAnalysis, Long>,
    LoadTestAnalysisPort {
    override fun findTopByGitlabConfigIdAndGitlabProjectIdOrderByCreatedAtDesc(
        gitlabConfigId: Long,
        gitlabProjectId: Long,
    ): LoadTestAnalysis?

    @Query("SELECT e FROM LoadTestAnalysis e WHERE e.repoConfigId = :repoConfigId ORDER BY e.createdAt DESC LIMIT 1")
    override fun findTopByRepoConfigIdOrderByCreatedAtDesc(
        @Param("repoConfigId") repoConfigId: Long,
    ): LoadTestAnalysis?

    @Query("SELECT e FROM LoadTestAnalysis e WHERE e.githubRepoConfigId = :githubRepoConfigId ORDER BY e.createdAt DESC LIMIT 1")
    override fun findTopByGithubRepoConfigIdOrderByCreatedAtDesc(
        @Param("githubRepoConfigId") githubRepoConfigId: Long,
    ): LoadTestAnalysis?

    @Query("SELECT e FROM LoadTestAnalysis e WHERE e.id = :id")
    override fun findLoadTestAnalysisById(
        @Param("id") id: Long,
    ): LoadTestAnalysis?

    override fun findAllLoadTestAnalysesOrderByCreatedAtDesc(): List<LoadTestAnalysis> =
        findAll(
            org.springframework.data.domain.Sort
                .by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"),
        )

    override fun findRecentLoadTestAnalyses(limit: Int): List<LoadTestAnalysis> =
        findAll(
            PageRequest.of(
                0,
                limit,
                org.springframework.data.domain.Sort
                    .by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"),
            ),
        ).content

    override fun saveLoadTestAnalysis(analysis: LoadTestAnalysis): LoadTestAnalysis = save(analysis)

    override fun deleteLoadTestAnalysisById(id: Long) = deleteById(id)

    override fun deleteAllLoadTestAnalyses() = deleteAll()
}
