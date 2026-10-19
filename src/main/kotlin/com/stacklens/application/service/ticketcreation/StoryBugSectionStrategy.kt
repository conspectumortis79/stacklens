package com.stacklens.application.service.ticketcreation
import com.stacklens.application.stereotype.ApplicationService

@ApplicationService
class StoryBugSectionStrategy : IssueTypeSectionStrategy {
    override fun matches(issueType: String): Boolean = issueType == "Story" || issueType == "Bug"

    override fun sectionNames(): List<String> =
        listOf(
            "Acceptance Criteria",
            "Akzeptanzkriterien",
            "Abnahmekriterien",
        )
}
