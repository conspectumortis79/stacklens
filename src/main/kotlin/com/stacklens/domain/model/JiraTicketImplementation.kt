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
@Table(name = "jira_ticket_implementations", indexes = [Index(columnList = "createdAt")])
class JiraTicketImplementation(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(nullable = false)
    var ticketKey: String,
    var jiraConfigId: Long? = null,
    @Column(columnDefinition = "TEXT")
    var gitlabConfigIds: String? = null,
    @Column(columnDefinition = "TEXT")
    var gitlabProjectIds: String? = null,
    @Column(columnDefinition = "TEXT")
    var localRepoConfigIds: String? = null,
    @Column(columnDefinition = "TEXT")
    var githubRepoConfigIds: String? = null,
    @Column(nullable = false)
    var status: String = "PENDING",
    var currentStep: String? = null,
    @Column(columnDefinition = "TEXT")
    var errorMessage: String? = null,
    @Column(columnDefinition = "TEXT")
    var ticketJson: String? = null,
    @Column(columnDefinition = "TEXT")
    var problemAnalysisJson: String? = null,
    @Column(columnDefinition = "TEXT")
    var similarTicketsJson: String? = null,
    @Column(columnDefinition = "TEXT")
    var relatedMrsJson: String? = null,
    @Column(columnDefinition = "TEXT")
    var finalAnalysis: String? = null,
    @Column(columnDefinition = "TEXT")
    var processLogJson: String? = null,
    @Column(columnDefinition = "TEXT")
    var codeImplementationJson: String? = null,
    @Column(length = 50)
    var codeLanguage: String? = null,
    @Column(length = 500)
    var monorepoSubPath: String? = null,
    @Column(columnDefinition = "TEXT")
    var monorepoAnalysisJson: String? = null,
    @Column(columnDefinition = "TEXT")
    var implementationPlanJson: String? = null,
    @Column(columnDefinition = "TEXT")
    var codeVerificationJson: String? = null,
    @Column(columnDefinition = "TEXT")
    var mrUrl: String? = null,
    @Column(columnDefinition = "BOOLEAN DEFAULT FALSE")
    var runDockerAfterImplementation: Boolean = false,
    @Column(columnDefinition = "BOOLEAN DEFAULT FALSE")
    var generateSystemTests: Boolean = false,
    @Column(columnDefinition = "TEXT")
    var dockerTestOutputJson: String? = null,
    @Column(columnDefinition = "TEXT")
    var liveDockerOutput: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    var completedAt: LocalDateTime? = null,
    @Column(columnDefinition = "TEXT")
    var rawInputText: String? = null,
)
