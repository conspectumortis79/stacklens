package com.stacklens.adapter.out.persistence

import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.domain.model.GitlabConfig
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface
GitlabConfigRepository :
    JpaRepository<GitlabConfig, Long>,
    GitlabConfigPort {
    override fun findByActiveTrue(): List<GitlabConfig>

    @Query("SELECT e FROM GitlabConfig e WHERE e.id = :id")
    override fun findGitlabConfigById(
        @Param("id") id: Long,
    ): GitlabConfig?

    @Query("SELECT e FROM GitlabConfig e")
    override fun findAllGitlabConfigs(): List<GitlabConfig>

    @Query("SELECT COUNT(e) > 0 FROM GitlabConfig e WHERE e.active = true")
    override fun existsActiveGitlabConfig(): Boolean

    @Query("SELECT e FROM GitlabConfig e WHERE e.id IN :ids")
    override fun findAllGitlabConfigsByIds(
        @Param("ids") ids: Iterable<Long>,
    ): List<GitlabConfig>

    override fun saveGitlabConfig(config: GitlabConfig): GitlabConfig = save(config)

    override fun deleteGitlabConfigById(id: Long) = deleteById(id)

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM GitlabConfig e")
    override fun deleteAllGitlabConfigs()
}
