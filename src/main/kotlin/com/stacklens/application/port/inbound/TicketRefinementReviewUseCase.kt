package com.stacklens.application.port.inbound

import com.stacklens.domain.model.TicketRefinement

interface TicketRefinementReviewUseCase {
    fun postReviewAsComment(
        preparationId: Long,
        ticketKey: String,
    ): TicketRefinement
}
