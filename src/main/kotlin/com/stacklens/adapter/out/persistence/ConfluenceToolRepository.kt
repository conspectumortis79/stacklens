package com.stacklens.adapter.out.persistence

import com.stacklens.application.port.out.ConfluenceToolPort
import com.stacklens.domain.EntityConcurrentlyDeletedException
import com.stacklens.domain.model.ConfluenceTool
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Repository

@Repository
interface ConfluenceToolRepository :
    JpaRepository<ConfluenceTool, Long>,
    ConfluenceToolPort {
    override fun findAllByOrderByCreatedAtDesc(): List<ConfluenceTool>

    override fun findRecentConfluenceTools(limit: Int): List<ConfluenceTool> = findAll(PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"))).content

    @Query("SELECT e FROM ConfluenceTool e WHERE e.id = :id")
    override fun findConfluenceToolById(
        @Param("id") id: Long,
    ): ConfluenceTool?

    override fun saveConfluenceTool(tool: ConfluenceTool): ConfluenceTool =
        try {
            save(tool)
        } catch (e: ObjectOptimisticLockingFailureException) {
            throw EntityConcurrentlyDeletedException("ConfluenceTool was concurrently deleted: ${e.message}")
        }

    override fun deleteConfluenceTool(tool: ConfluenceTool) = delete(tool)

    override fun deleteAllConfluenceTools() = deleteAll()
}
