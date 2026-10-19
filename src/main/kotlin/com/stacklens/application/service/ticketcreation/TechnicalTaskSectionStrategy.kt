package com.stacklens.application.service.ticketcreation
import com.stacklens.application.stereotype.ApplicationService

@ApplicationService
class TechnicalTaskSectionStrategy : IssueTypeSectionStrategy {
    override fun matches(issueType: String): Boolean = issueType == "Technical Task"

    override fun sectionNames(): List<String> =
        listOf(
            "Acceptance Criteria",
            "Akzeptanzkriterien",
            "Abnahmekriterien",
        )
}
