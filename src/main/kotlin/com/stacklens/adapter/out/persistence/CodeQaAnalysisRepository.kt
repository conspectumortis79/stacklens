package com.stacklens.adapter.out.persistence

import com.stacklens.application.port.out.CodeQaAnalysisPort
import com.stacklens.domain.model.CodeQaAnalysis
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface CodeQaAnalysisRepository :
    JpaRepository<CodeQaAnalysis, Long>,
    CodeQaAnalysisPort {
    override fun findAllByOrderByCreatedAtDesc(): List<CodeQaAnalysis>

    override fun findRecent(limit: Int): List<CodeQaAnalysis> = findAll(PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"))).content

    @Query("SELECT e FROM CodeQaAnalysis e WHERE e.id = :id")
    override fun findCodeQaAnalysisById(
        @Param("id") id: Long,
    ): CodeQaAnalysis?

    override fun saveCodeQaAnalysis(analysis: CodeQaAnalysis): CodeQaAnalysis = save(analysis)

    override fun deleteCodeQaAnalysisById(id: Long) = deleteById(id)

    override fun deleteAllCodeQaAnalyses() = deleteAll()
}
