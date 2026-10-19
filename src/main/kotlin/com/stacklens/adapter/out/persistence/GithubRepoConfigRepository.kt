package com.stacklens.adapter.out.persistence

import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.domain.model.GithubRepoConfig
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface GithubRepoConfigRepository :
    JpaRepository<GithubRepoConfig, Long>,
    GithubRepoConfigPort {
    @Query("SELECT e FROM GithubRepoConfig e")
    override fun findAllGithubRepoConfigs(): List<GithubRepoConfig>

    @Query("SELECT e FROM GithubRepoConfig e WHERE e.id = :id")
    override fun findGithubRepoConfigById(
        @Param("id") id: Long,
    ): GithubRepoConfig?

    @Query("SELECT e FROM GithubRepoConfig e WHERE e.id IN :ids")
    override fun findAllGithubRepoConfigsByIds(
        @Param("ids") ids: Iterable<Long>,
    ): List<GithubRepoConfig>

    @Query("SELECT e FROM GithubRepoConfig e WHERE e.active = true")
    override fun findByActiveTrue(): List<GithubRepoConfig>

    override fun saveGithubRepoConfig(config: GithubRepoConfig): GithubRepoConfig = save(config)

    override fun githubRepoConfigExistsById(id: Long): Boolean = existsById(id)

    override fun deleteGithubRepoConfigById(id: Long) = deleteById(id)

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM GithubRepoConfig e")
    override fun deleteAllGithubRepoConfigs()
}
