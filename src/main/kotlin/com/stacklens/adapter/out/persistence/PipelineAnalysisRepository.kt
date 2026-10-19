package com.stacklens.adapter.out.persistence

import com.stacklens.application.port.out.PipelineAnalysisPort
import com.stacklens.domain.model.PipelineAnalysis
import org.springframework.data.domain.PageRequest
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface PipelineAnalysisRepository :
    JpaRepository<PipelineAnalysis, Long>,
    PipelineAnalysisPort {
    @Query("SELECT e FROM PipelineAnalysis e WHERE e.id = :id")
    override fun findPipelineAnalysisById(
        @Param("id") id: Long,
    ): PipelineAnalysis?

    override fun findAllPipelineAnalysesOrderByCreatedAtDesc(): List<PipelineAnalysis> =
        findAll(
            org.springframework.data.domain.Sort
                .by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"),
        )

    override fun findRecentPipelineAnalyses(limit: Int): List<PipelineAnalysis> =
        findAll(
            PageRequest.of(
                0,
                limit,
                org.springframework.data.domain.Sort
                    .by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"),
            ),
        ).content

    @Query("SELECT e FROM PipelineAnalysis e WHERE e.repoConfigId = :repoConfigId ORDER BY e.createdAt DESC LIMIT 1")
    override fun findTopByRepoConfigIdOrderByCreatedAtDesc(
        @Param("repoConfigId") repoConfigId: Long,
    ): PipelineAnalysis?

    @Query(
        "SELECT e FROM PipelineAnalysis e WHERE e.gitlabConfigId = :gitlabConfigId " +
            "AND e.gitlabProjectId = :gitlabProjectId ORDER BY e.createdAt DESC LIMIT 1",
    )
    override fun findTopByGitlabConfigIdAndGitlabProjectIdOrderByCreatedAtDesc(
        @Param("gitlabConfigId") gitlabConfigId: Long,
        @Param("gitlabProjectId") gitlabProjectId: Long,
    ): PipelineAnalysis?

    @Query("SELECT e FROM PipelineAnalysis e WHERE e.githubRepoConfigId = :githubRepoConfigId ORDER BY e.createdAt DESC LIMIT 1")
    override fun findTopByGithubRepoConfigIdOrderByCreatedAtDesc(
        @Param("githubRepoConfigId") githubRepoConfigId: Long,
    ): PipelineAnalysis?

    override fun savePipelineAnalysis(analysis: PipelineAnalysis): PipelineAnalysis = save(analysis)

    override fun deletePipelineAnalysisById(id: Long) = deleteById(id)

    override fun deleteAllPipelineAnalyses() = deleteAll()
}
