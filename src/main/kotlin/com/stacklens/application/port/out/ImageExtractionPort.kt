package com.stacklens.application.port.out

import com.stacklens.domain.model.ImageExtractionRecord

interface ImageExtractionPort {
    fun findAllByOrderByCreatedAtDesc(): List<ImageExtractionRecord>

    fun findRecentImageExtractions(limit: Int): List<ImageExtractionRecord>

    fun findImageExtractionById(id: Long): ImageExtractionRecord?

    fun saveImageExtraction(record: ImageExtractionRecord): ImageExtractionRecord

    fun deleteImageExtractionById(id: Long)

    fun deleteAllImageExtractions()
}
