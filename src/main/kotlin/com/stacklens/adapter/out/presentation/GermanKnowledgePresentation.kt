package com.stacklens.adapter.out.presentation

import com.stacklens.application.port.out.knowledge.ReviewKnowledgePresentationPort
import com.stacklens.application.port.out.knowledge.SprintKnowledgePresentationPort
import com.stacklens.application.port.out.knowledge.TicketCreationKnowledgePresentationPort
import com.stacklens.application.port.out.knowledge.TicketRefinementKnowledgePresentationPort
import com.stacklens.application.port.out.knowledge.TicketSummaryKnowledgePresentationPort

object GermanKnowledgePresentation :
    ReviewKnowledgePresentationPort,
    SprintKnowledgePresentationPort,
    TicketCreationKnowledgePresentationPort,
    TicketRefinementKnowledgePresentationPort,
    TicketSummaryKnowledgePresentationPort {
    override fun reviewAnalysisTitle(id: Long): String = "Review-Analyse #$id"

    override fun reviewAnalysisHeader(): String = "# Review-Analyse"

    override fun reviewAnalysisRoute(id: Long): String? = "/review-analysis?id=$id"

    override fun sprintUrlLabel(): String = "Sprint-URL"

    override fun jiraConfigLabel(): String = "Jira-Konfiguration"

    override fun instructionsHeading(): String = "## Anweisungen"

    override fun resultHeading(): String = "## Ergebnis"

    override fun sprintReviewTitle(sprintName: String): String = "Sprint-Review: $sprintName"

    override fun sprintReviewHeader(): String = "# Sprint-Review"

    override fun targetTicketLabel(): String = "Ziel-Ticket"

    override fun sprintStateLabel(): String = "Sprint-Status"

    override fun issueCountLabel(): String = "Anzahl Tickets"

    override fun rowHeading(
        key: String,
        summary: String,
    ): String = "## $key – $summary"

    override fun statusLabel(): String = "Status"

    override fun improvementLabel(): String = "Verbesserung"

    override fun openQuestionsHeading(): String = "Offene Fragen"

    override fun ticketCreationTitle(summary: String): String = "Ticket-Erstellung: $summary"

    override fun ticketCreationHeader(): String = "# Ticket-Erstellung"

    override fun summaryLabel(): String = "Zusammenfassung"

    override fun typeLabel(): String = "Typ"

    override fun labelsLabel(): String = "Labels"

    override fun jiraKeyLabel(): String = "Jira-Key"

    override fun jiraUrlLabel(): String = "Jira-URL"

    override fun acceptanceCriteriaHeading(): String = "## Akzeptanzkriterien"

    override fun descriptionHeading(): String = "## Beschreibung"

    override fun openPointsHeading(): String = "## Offene Punkte"

    override fun bullet(value: String): String = "- $value"

    override fun ticketRefinementTitle(ticketKey: String): String = "Ticket-Aufbereitung $ticketKey"

    override fun ticketRefinementHeader(): String = "# Ticket-Aufbereitung"

    override fun parentLabel(): String = "Parent"

    override fun originalTitleHeading(): String = "## Originaltitel"

    override fun originalDescriptionHeading(): String = "## Originalbeschreibung"

    override fun improvedDescriptionHeading(): String = "## Verbesserte Beschreibung"

    override fun suggestedSubtasksHeading(): String = "## Vorgeschlagene Subtasks"

    override fun ticketSummaryTitle(ticketKey: String): String = "Ticket-Zusammenfassung: $ticketKey"
}
