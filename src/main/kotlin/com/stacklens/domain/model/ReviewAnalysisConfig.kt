package com.stacklens.domain.model
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "review_analysis_configs")
class ReviewAnalysisConfig(
    @Id
    val id: Long = 1,
    @Column(columnDefinition = "TEXT")
    var jiraSprintUrl: String = "",
    var jiraConfigId: Long? = null,
    @Column(columnDefinition = "TEXT")
    var analysisInstructions: String = "",
    var updatedAt: LocalDateTime = LocalDateTime.now(),
)
