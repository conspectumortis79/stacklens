package com.stacklens.adapter.out.persistence

import com.stacklens.application.port.out.GrafanaConfigPort
import com.stacklens.domain.model.GrafanaConfig
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface GrafanaConfigRepository :
    JpaRepository<GrafanaConfig, Long>,
    GrafanaConfigPort {
    override fun findByActiveTrue(): List<GrafanaConfig>

    @Query("SELECT e FROM GrafanaConfig e")
    override fun findAllGrafanaConfigs(): List<GrafanaConfig>

    @Query("SELECT e FROM GrafanaConfig e WHERE e.id = :id")
    override fun findGrafanaConfigById(
        @Param("id") id: Long,
    ): GrafanaConfig?

    @Query("SELECT e FROM GrafanaConfig e WHERE e.id IN :ids")
    override fun findAllGrafanaConfigsByIds(
        @Param("ids") ids: Iterable<Long>,
    ): List<GrafanaConfig>

    override fun saveGrafanaConfig(config: GrafanaConfig): GrafanaConfig = save(config)

    override fun deleteGrafanaConfigById(id: Long) = deleteById(id)

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM GrafanaConfig e")
    override fun deleteAllGrafanaConfigs()
}
