package com.stacklens.adapter.out.persistence

import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.domain.model.McpServerConfig
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface McpServerConfigRepository :
    JpaRepository<McpServerConfig, Long>,
    McpServerConfigPort {
    @Query("SELECT e FROM McpServerConfig e ORDER BY e.id ASC")
    override fun findAllMcpServerConfigs(): List<McpServerConfig>

    @Query("SELECT e FROM McpServerConfig e WHERE e.id = :id")
    override fun findMcpServerConfigById(id: Long): McpServerConfig?

    @Query("SELECT e FROM McpServerConfig e WHERE e.active = TRUE ORDER BY e.id ASC LIMIT 1")
    override fun findFirstActiveMcpServerConfig(): McpServerConfig?

    override fun saveMcpServerConfig(config: McpServerConfig): McpServerConfig = save(config)

    override fun deleteMcpServerConfigById(id: Long) = deleteById(id)

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM McpServerConfig e")
    override fun deleteAllMcpServerConfigs()
}
