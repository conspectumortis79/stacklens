package com.stacklens.application.port.out.knowledge

import com.stacklens.domain.model.JiraConfig

interface JiraHierarchyPort {
    fun ensureHostMatches(
        config: JiraConfig,
        rootTicketKeyOrUrl: String,
    )

    fun walkHierarchy(
        config: JiraConfig,
        rootTicketKeyOrUrl: String,
        includeChildren: Boolean,
        includeComments: Boolean,
        maxDepth: Int,
        maxIssues: Int = 1000,
        includeLinkedTickets: Boolean = true,
        includeParent: Boolean = false,
    ): Sequence<JiraIssueSnapshot>
}

data class JiraIssueSnapshot(
    val key: String,
    val title: String,
    val sourceUri: String,
    val plainText: String,
    val depth: Int,
)
