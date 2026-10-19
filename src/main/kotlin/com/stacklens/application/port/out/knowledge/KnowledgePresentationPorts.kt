package com.stacklens.application.port.out.knowledge

interface ReviewKnowledgePresentationPort {
    fun reviewAnalysisTitle(id: Long): String

    fun reviewAnalysisHeader(): String

    fun reviewAnalysisRoute(id: Long): String?

    fun sprintUrlLabel(): String

    fun jiraConfigLabel(): String

    fun instructionsHeading(): String

    fun resultHeading(): String
}

interface SprintKnowledgePresentationPort {
    fun sprintReviewTitle(sprintName: String): String

    fun sprintReviewHeader(): String

    fun targetTicketLabel(): String

    fun sprintStateLabel(): String

    fun issueCountLabel(): String

    fun rowHeading(
        key: String,
        summary: String,
    ): String

    fun statusLabel(): String

    fun improvementLabel(): String

    fun openQuestionsHeading(): String

    fun bullet(value: String): String
}

interface TicketCreationKnowledgePresentationPort {
    fun ticketCreationTitle(summary: String): String

    fun ticketCreationHeader(): String

    fun summaryLabel(): String

    fun typeLabel(): String

    fun labelsLabel(): String

    fun jiraKeyLabel(): String

    fun jiraUrlLabel(): String

    fun acceptanceCriteriaHeading(): String

    fun descriptionHeading(): String

    fun openPointsHeading(): String

    fun bullet(value: String): String
}

interface TicketRefinementKnowledgePresentationPort {
    fun ticketRefinementTitle(ticketKey: String): String

    fun ticketRefinementHeader(): String

    fun parentLabel(): String

    fun originalTitleHeading(): String

    fun originalDescriptionHeading(): String

    fun improvedDescriptionHeading(): String

    fun suggestedSubtasksHeading(): String

    fun openPointsHeading(): String

    fun bullet(value: String): String
}

interface TicketSummaryKnowledgePresentationPort {
    fun ticketSummaryTitle(ticketKey: String): String
}
