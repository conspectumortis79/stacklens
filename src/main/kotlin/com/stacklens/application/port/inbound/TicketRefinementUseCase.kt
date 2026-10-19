package com.stacklens.application.port.inbound

interface TicketRefinementUseCase :
    TicketRefinementLifecycleUseCase,
    TicketRefinementMetadataUseCase,
    TicketRefinementReviewUseCase,
    TicketRefinementPostingUseCase

data class RepoSelection(
    val type: String,
    val id: Long,
    val projectId: Long? = null,
)
