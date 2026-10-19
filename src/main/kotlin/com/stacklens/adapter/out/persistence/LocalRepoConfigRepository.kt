package com.stacklens.adapter.out.persistence

import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.domain.model.LocalRepoConfig
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface LocalRepoConfigRepository :
    JpaRepository<LocalRepoConfig, Long>,
    LocalRepoConfigPort {
    override fun findAllByScope(scope: String): List<LocalRepoConfig>

    @Query("SELECT e FROM LocalRepoConfig e WHERE e.id = :id")
    override fun findLocalRepoConfigById(
        @Param("id") id: Long,
    ): LocalRepoConfig?

    @Query("SELECT e FROM LocalRepoConfig e")
    override fun findAllLocalRepoConfigs(): List<LocalRepoConfig>

    override fun findFirstByPathAndScope(
        path: String,
        scope: String,
    ): LocalRepoConfig? = findAll().firstOrNull { it.path == path && it.scope == scope }

    override fun saveLocalRepoConfig(config: LocalRepoConfig): LocalRepoConfig = save(config)

    override fun localRepoConfigExistsById(id: Long): Boolean = existsById(id)

    override fun deleteLocalRepoConfigById(id: Long) = deleteById(id)

    override fun deleteAllLocalRepoConfigs() = deleteAll()
}
