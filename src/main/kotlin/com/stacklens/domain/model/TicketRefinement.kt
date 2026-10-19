
package com.stacklens.domain.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "ticket_refinements", indexes = [Index(columnList = "createdAt")])
class TicketRefinement(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(nullable = false)
    var parentTicketKey: String,
    var jiraConfigId: Long? = null,
    @Column(columnDefinition = "TEXT")
    var templateOverride: String? = null,
    @Column(nullable = false)
    var singleTicketOnly: Boolean = true,
    @Column(nullable = false)
    var status: String = "PENDING",
    var currentStep: String? = null,
    @Column(columnDefinition = "TEXT")
    var errorMessage: String? = null,
    @Column(columnDefinition = "TEXT")
    var parentTicketJson: String? = null,
    @Column(columnDefinition = "TEXT")
    var reviewsJson: String? = null,
    @Column(columnDefinition = "TEXT")
    var repoSelectionsJson: String? = null,
    var databaseConfigId: Long? = null,
    @Column(columnDefinition = "TEXT")
    var databaseConfigIds: String? = null,
    @Column(columnDefinition = "TEXT")
    var subtasksCreatedJson: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    var completedAt: LocalDateTime? = null,
)

data class TicketReview(
    val ticketKey: String,
    val ticketUrl: String,
    val originalSummary: String,
    val originalDescription: String,
    val improvedDescription: String,
    val openPoints: List<String>,
    val state: String,
    val errorMessage: String? = null,
    val commentPosted: Boolean = false,
    val originalDescriptionHtml: String? = null,
    val improvedDescriptionHtml: String? = null,
    val repoAnalyses: List<RepoAnalysis> = emptyList(),
    val improvedNative: String = "",
    val improvedFormat: String = "markdown",
    val improvedSummary: String = "",
    val suggestedSubtasks: List<SubtaskSuggestion> = emptyList(),
)

data class SubtaskSuggestion(
    val summary: String,
    val description: String = "",
    val issueType: String = "Subtask",
    val priority: String = "Minor",
    val labels: List<String> = emptyList(),
    val existingKey: String = "",
)

data class RepoAnalysis(
    val repoRef: String,
    val repoName: String,
    val repoType: String,
    val relevant: Boolean,
    val summary: String,
    val suggestedChanges: List<String> = emptyList(),
    val dependencies: List<String> = emptyList(),
    val hiddenDependencies: List<String> = emptyList(),
    val complexity: String = "",
    val state: String = if (relevant) "OK" else "SKIPPED",
    val errorMessage: String? = null,
)
