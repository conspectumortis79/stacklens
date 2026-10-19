package com.stacklens.adapter.out.persistence

import com.stacklens.application.port.out.DatabaseConfigPort
import com.stacklens.domain.model.DatabaseConfig
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface DatabaseConfigRepository :
    JpaRepository<DatabaseConfig, Long>,
    DatabaseConfigPort {
    override fun findByActiveTrue(): List<DatabaseConfig>

    @Query("SELECT e FROM DatabaseConfig e WHERE e.id = :id")
    override fun findDatabaseConfigById(
        @Param("id") id: Long,
    ): DatabaseConfig?

    @Query("SELECT e FROM DatabaseConfig e")
    override fun findAllDatabaseConfigs(): List<DatabaseConfig>

    @Query("SELECT e FROM DatabaseConfig e WHERE e.id IN :ids")
    override fun findAllDatabaseConfigsByIds(
        @Param("ids") ids: Iterable<Long>,
    ): List<DatabaseConfig>

    override fun saveDatabaseConfig(config: DatabaseConfig): DatabaseConfig = save(config)

    override fun deleteDatabaseConfigById(id: Long) = deleteById(id)

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM DatabaseConfig e")
    override fun deleteAllDatabaseConfigs()
}
