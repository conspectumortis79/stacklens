package com.stacklens.domain.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "pipeline_analyses")
class PipelineAnalysis(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    override val id: Long = 0,
    val repoConfigId: Long? = null,
    var gitlabConfigId: Long? = null,
    var gitlabProjectId: Long? = null,
    @Column(columnDefinition = "TEXT")
    var gitlabProjectPath: String? = null,
    var githubRepoConfigId: Long? = null,
    @Column(nullable = false)
    override var status: String = "PENDING",
    @Column(columnDefinition = "TEXT")
    var summaryText: String? = null,
    @Column(columnDefinition = "TEXT")
    var findingsJson: String? = null,
    @Column(columnDefinition = "TEXT")
    var fileDescriptionsJson: String? = null,
    @Column(columnDefinition = "TEXT")
    var optimizationsJson: String? = null,
    @Column(columnDefinition = "TEXT")
    var mermaid: String? = null,
    @Column(columnDefinition = "TEXT")
    var errorMessage: String? = null,
    var modelUsed: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    var completedAt: LocalDateTime? = null,
    @Column(columnDefinition = "TEXT")
    var userPromptAdditions: String? = null,
    @Column(columnDefinition = "TEXT")
    var mrUrl: String? = null,
    @Column(columnDefinition = "TEXT")
    var mrBranch: String? = null,
    @Column(length = 32)
    var mrProvider: String? = null,
) : AnalysisRecord
