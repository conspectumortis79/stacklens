package com.stacklens.adapter.out.persistence

import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.domain.model.AiConfig
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface AiConfigRepository :
    JpaRepository<AiConfig, Long>,
    AiConfigPort {
    @Query(value = "SELECT * FROM ai_config ORDER BY id ASC LIMIT 1", nativeQuery = true)
    override fun findAiConfig(): AiConfig?

    override fun saveAiConfig(config: AiConfig): AiConfig = saveAndFlush(config)

    override fun deleteAiConfig() = deleteAll()
}
