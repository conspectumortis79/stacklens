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
@Table(name = "sprint_reviews", indexes = [Index(columnList = "createdAt")])
class SprintReview(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(nullable = false, columnDefinition = "TEXT")
    var sprintName: String,
    @Column(nullable = false)
    var targetTicketKey: String,
    var jiraConfigId: Long? = null,
    @Column(columnDefinition = "TEXT")
    var templateOverride: String? = null,
    @Column(nullable = false)
    var includeImprovement: Boolean = true,
    @Column(nullable = false)
    var status: String = "PENDING",
    var currentStep: String? = null,
    @Column(columnDefinition = "TEXT")
    var errorMessage: String? = null,
    var sprintId: Long? = null,
    @Column(columnDefinition = "TEXT")
    var sprintState: String? = null,
    var totalIssues: Int = 0,
    @Column(columnDefinition = "TEXT")
    var targetTicketUrl: String? = null,
    @Column(columnDefinition = "TEXT")
    var sprintUrl: String? = null,
    @Column(columnDefinition = "TEXT")
    var rowsJson: String? = null,
    @Column(columnDefinition = "TEXT")
    var tableAdfJson: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    var completedAt: LocalDateTime? = null,
)

enum class SprintReviewState {
    BEARBEITET,
    OFFENE_FRAGE,
    REFINEMENT,
    FAILED,
    ;

    companion object {
        fun fromAiState(raw: String): SprintReviewState =
            when (raw.uppercase()) {
                "BEARBEITET" -> BEARBEITET
                "OFFENE_FRAGE" -> OFFENE_FRAGE
                "REFINEMENT" -> REFINEMENT
                else -> FAILED
            }
    }
}

data class SprintReviewRow(
    val ticketKey: String,
    val ticketSummary: String,
    val ticketUrl: String,
    val issueType: String,
    val status: String,
    val assignee: String? = null,
    val storyPoints: Double? = null,
    val state: SprintReviewState,
    val improvementSummary: String = "",
    val openPoints: List<String> = emptyList(),
    val refinementReasons: List<String> = emptyList(),
    val errorMessage: String? = null,
)
