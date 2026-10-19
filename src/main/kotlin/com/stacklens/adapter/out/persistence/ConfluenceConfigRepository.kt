package com.stacklens.adapter.out.persistence

import com.stacklens.application.port.out.ConfluenceConfigPort
import com.stacklens.domain.model.ConfluenceConfig
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ConfluenceConfigRepository :
    JpaRepository<ConfluenceConfig, Long>,
    ConfluenceConfigPort {
    override fun findByActiveTrue(): List<ConfluenceConfig>

    @Query("SELECT e FROM ConfluenceConfig e WHERE e.id = :id")
    override fun findConfluenceConfigById(
        @Param("id") id: Long,
    ): ConfluenceConfig?

    @Query("SELECT e FROM ConfluenceConfig e")
    override fun findAllConfluenceConfigs(): List<ConfluenceConfig>

    @Query("SELECT e FROM ConfluenceConfig e WHERE e.id IN :ids")
    override fun findAllConfluenceConfigsByIds(
        @Param("ids") ids: Iterable<Long>,
    ): List<ConfluenceConfig>

    override fun saveConfluenceConfig(config: ConfluenceConfig): ConfluenceConfig = save(config)

    override fun deleteConfluenceConfigById(id: Long) = deleteById(id)

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM ConfluenceConfig e")
    override fun deleteAllConfluenceConfigs()
}
