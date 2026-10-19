package com.stacklens.adapter.out.persistence

import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.domain.model.JiraConfig
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface JiraConfigRepository :
    JpaRepository<JiraConfig, Long>,
    JiraConfigPort {
    override fun findByActiveTrue(): List<JiraConfig>

    override fun findFirstByActiveTrue(): JiraConfig?

    @Query("SELECT e FROM JiraConfig e WHERE e.id = :id")
    override fun findJiraConfigById(
        @Param("id") id: Long,
    ): JiraConfig?

    @Query("SELECT e FROM JiraConfig e")
    override fun findAllJiraConfigs(): List<JiraConfig>

    @Query("SELECT e FROM JiraConfig e WHERE e.id IN :ids")
    override fun findAllJiraConfigsByIds(
        @Param("ids") ids: Iterable<Long>,
    ): List<JiraConfig>

    override fun saveJiraConfig(config: JiraConfig): JiraConfig = save(config)

    override fun jiraConfigExistsById(id: Long): Boolean = existsById(id)

    override fun deleteJiraConfigById(id: Long) = deleteById(id)

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM JiraConfig e")
    override fun deleteAllJiraConfigs()
}
