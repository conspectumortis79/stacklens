package com.stacklens.adapter.out.persistence

import com.stacklens.application.port.out.ImageExtractionPort
import com.stacklens.domain.model.ImageExtractionRecord
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ImageExtractionRepository :
    JpaRepository<ImageExtractionRecord, Long>,
    ImageExtractionPort {
    override fun findAllByOrderByCreatedAtDesc(): List<ImageExtractionRecord>

    override fun findRecentImageExtractions(limit: Int): List<ImageExtractionRecord> = findAll(PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"))).content

    @Query("SELECT e FROM ImageExtractionRecord e WHERE e.id = :id")
    override fun findImageExtractionById(
        @Param("id") id: Long,
    ): ImageExtractionRecord?

    override fun saveImageExtraction(record: ImageExtractionRecord): ImageExtractionRecord = save(record)

    override fun deleteImageExtractionById(id: Long) = deleteById(id)

    override fun deleteAllImageExtractions() = deleteAll()
}
