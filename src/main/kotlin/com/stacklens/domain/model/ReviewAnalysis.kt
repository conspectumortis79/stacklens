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
@Table(name = "review_analyses", indexes = [Index(columnList = "createdAt")])
class ReviewAnalysis(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(nullable = false, columnDefinition = "TEXT")
    var jiraSprintUrl: String,
    var jiraConfigId: Long? = null,
    @Column(columnDefinition = "TEXT")
    var analysisInstructions: String = "",
    @Column(nullable = false)
    var status: String = "PENDING",
    var currentStep: String? = null,
    @Column(columnDefinition = "TEXT")
    var errorMessage: String? = null,
    @Column(columnDefinition = "TEXT")
    var sprintInfoJson: String? = null,
    @Column(columnDefinition = "TEXT")
    var sprintIssuesJson: String? = null,
    @Column(columnDefinition = "TEXT")
    var resultMarkdown: String? = null,
    @Column(columnDefinition = "TEXT")
    var processLogJson: String? = null,
    @Column(length = 255)
    var pptxFileName: String? = null,
    @Column(length = 1024)
    var pptxServerPath: String? = null,
    var pptxSlideCount: Int? = null,
    @Column(length = 1024)
    var resultHtmlPath: String? = null,
    @Column(columnDefinition = "TEXT")
    var pptxQuestionsJson: String? = null,
    @Column(columnDefinition = "TEXT")
    var pptxAnswersJson: String? = null,
    @Column(length = 8)
    var pptxDetectedLanguage: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    var completedAt: LocalDateTime? = null,
)
