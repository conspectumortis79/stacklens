package com.stacklens.domain.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "load_test_analyses")
class LoadTestAnalysis(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    override val id: Long = 0,
    var gitlabConfigId: Long? = null,
    var gitlabProjectId: Long? = null,
    @Column(columnDefinition = "TEXT")
    var gitlabProjectPath: String? = null,
    var repoConfigId: Long? = null,
    var githubRepoConfigId: Long? = null,
    @Column(nullable = false)
    override var status: String = "PENDING",
    @Column(columnDefinition = "TEXT")
    var summaryText: String? = null,
    @Column(columnDefinition = "TEXT")
    var findingsJson: String? = null,
    @Column(columnDefinition = "TEXT")
    var k6Script: String? = null,
    @Column(columnDefinition = "TEXT")
    var environmentsJson: String? = null,
    @Column(columnDefinition = "TEXT")
    var errorMessage: String? = null,
    var modelUsed: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    var completedAt: LocalDateTime? = null,
    @Column(columnDefinition = "TEXT")
    var k6Output: String? = null,
    @Column(columnDefinition = "TEXT")
    var k6Error: String? = null,
    var k6ExitCode: Int? = null,
    @Column(columnDefinition = "TEXT")
    var k6SummaryJson: String? = null,
) : AnalysisRecord
