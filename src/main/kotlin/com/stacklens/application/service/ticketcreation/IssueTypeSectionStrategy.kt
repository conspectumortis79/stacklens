package com.stacklens.application.service.ticketcreation

interface IssueTypeSectionStrategy {
    fun matches(issueType: String): Boolean

    fun sectionNames(): List<String>
}
