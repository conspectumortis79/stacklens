package com.stacklens.application.service.ticketrefinement

data class TicketRefinementCollaborators(
    val fieldFormatter: TicketFieldFormatting,
    val reviewEngine: TicketReviewEngine,
    val repoAnalyser: TicketRepoAnalyser,
    val commentPoster: TicketCommentPoster,
    val lifecycle: TicketRefinementLifecycle,
    val subtaskLedger: SubtaskLedger,
    val subtaskCoordinator: TicketRefinementSubtaskCoordinator,
)
