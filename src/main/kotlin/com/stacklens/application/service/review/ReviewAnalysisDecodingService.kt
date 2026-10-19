package com.stacklens.application.service.review

import com.stacklens.application.port.inbound.review.ReviewAnalysisDecodingUseCase
import com.stacklens.application.port.out.JsonTreeReaderPort
import com.stacklens.application.stereotype.ApplicationService

@ApplicationService
class ReviewAnalysisDecodingService(
    private val jsonCodec: JsonTreeReaderPort,
) : ReviewAnalysisDecodingUseCase {
    override fun sprintName(sprintInfoJson: String?): String? = jsonCodec.extractStringField(sprintInfoJson, SPRINT_NAME_FIELD)

    override fun processLogEntries(processLogJson: String?): List<Any?> = jsonCodec.parseArrayOrEmpty(processLogJson)

    private companion object {
        const val SPRINT_NAME_FIELD: String = "name"
    }
}
