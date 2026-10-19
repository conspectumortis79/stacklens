package com.stacklens.application.service

import com.stacklens.application.port.inbound.imageextraction.ImageExtractionHistoryUseCase
import com.stacklens.application.port.out.ImageExtractionPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.ImageExtractionRecord

@ApplicationService
class ImageExtractionHistoryService(
    private val repo: ImageExtractionPort,
) : ImageExtractionHistoryUseCase {
    override fun getAllRecords(): List<ImageExtractionRecord> = repo.findRecentImageExtractions(AnalysisConstants.MAX_RECENT_RECORDS)

    override fun getRecord(id: Long): ImageExtractionRecord? = repo.findImageExtractionById(id)

    override fun deleteRecord(id: Long) = repo.deleteImageExtractionById(id)

    override fun deleteAllRecords() = repo.deleteAllImageExtractions()
}
