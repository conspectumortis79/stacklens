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
@Table(
    name = "logging_fix_analyses",
    indexes = [Index(columnList = "createdAt")],
)
class LoggingFixAnalysis(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(nullable = false)
    var featureSlug: String,
    @Column(nullable = false, columnDefinition = "TEXT")
    var logText: String,
    @Column(columnDefinition = "TEXT")
    var selectedCategories: String,
    var localRepoConfigId: Long? = null,
    @Column(nullable = false, length = 20)
    var status: String = STATUS_PENDING,
    @Column(columnDefinition = "TEXT")
    var errorMessage: String? = null,
    @Column(columnDefinition = "TEXT")
    var stageDiagnose: String? = null,
    @Column(columnDefinition = "TEXT")
    var stagePlan: String? = null,
    @Column(columnDefinition = "TEXT")
    var stageCode: String? = null,
    @Column(columnDefinition = "TEXT")
    var stageTests: String? = null,
    @Column(columnDefinition = "TEXT")
    var stageStatusJson: String? = null,
    var language: String = "de",
    val createdAt: LocalDateTime = LocalDateTime.now(),
    var completedAt: LocalDateTime? = null,
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_RUNNING = "RUNNING"
        const val STATUS_DONE = "DONE"
        const val STATUS_ERROR = "ERROR"
        const val STATUS_CANCELLED = "CANCELLED"
    }
}
