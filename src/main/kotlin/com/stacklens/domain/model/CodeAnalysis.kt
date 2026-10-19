package com.stacklens.domain.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "code_analyses")
class CodeAnalysis(
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
    var fileChangesJson: String? = null,
    @Column(columnDefinition = "TEXT")
    var errorMessage: String? = null,
    var modelUsed: String? = null,
    @Column(columnDefinition = "TEXT")
    var mrUrl: String? = null,
    var mrBranch: String? = null,
    @Column(columnDefinition = "TEXT")
    var mermaid: String? = null,
    @Column(columnDefinition = "TEXT")
    var drawioXml: String? = null,
    @Column(columnDefinition = "TEXT")
    var monorepoSubPath: String? = null,
    var monorepoAppName: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    var completedAt: LocalDateTime? = null,
) : AnalysisRecord
