package com.stacklens.application.port.inbound

import com.stacklens.application.port.model.SubtaskDraft

interface TicketRefinementPostingUseCase {
    fun createSuggestedSubtasks(
        preparationId: Long,
        ticketKey: String,
        subtasks: List<SubtaskDraft>,
    ): Map<String, Any?>
}
