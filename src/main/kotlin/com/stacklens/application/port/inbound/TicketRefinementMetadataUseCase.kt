package com.stacklens.application.port.inbound

interface TicketRefinementMetadataUseCase {
    fun defaultPromptTemplate(): String

    fun firstActiveJiraConfigId(): Long?

    fun jiraConfigExists(id: Long): Boolean

    fun normalizeReviewsJson(json: String?): String?
}
