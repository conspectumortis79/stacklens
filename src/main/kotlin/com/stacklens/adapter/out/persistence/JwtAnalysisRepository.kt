package com.stacklens.adapter.out.persistence

import com.stacklens.application.port.out.JwtAnalysisPort
import com.stacklens.domain.model.JwtAnalysisRecord
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface JwtAnalysisRepository :
    JpaRepository<JwtAnalysisRecord, Long>,
    JwtAnalysisPort {
    override fun deleteAllJwtAnalyses() = deleteAll()
}
