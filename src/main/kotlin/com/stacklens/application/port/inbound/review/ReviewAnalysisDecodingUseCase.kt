package com.stacklens.application.port.inbound.review

interface ReviewAnalysisDecodingUseCase {
    fun sprintName(sprintInfoJson: String?): String?

    fun processLogEntries(processLogJson: String?): List<Any?>
}
