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
@Table(name = "mr_analyses", indexes = [Index(columnList = "createdAt")])
class CodeQaAnalysis(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(nullable = false, columnDefinition = "TEXT")
    var mrUrl: String,
    @Column(columnDefinition = "TEXT")
    var jiraTicketKey: String? = null,
    var jiraConfigId: Long? = null,
    var gitConfigId: Long? = null,
    @Column(length = 16)
    var gitPlatform: String? = null,
    @Column(columnDefinition = "TEXT")
    var additionalInstructions: String? = null,
    @Column(nullable = false)
    var status: String = "PENDING",
    @Column(columnDefinition = "TEXT")
    var currentStep: String = "",
    @Column(columnDefinition = "TEXT")
    var logOutput: String = "",
    @Column(columnDefinition = "TEXT")
    var resultMarkdown: String? = null,
    @Column(columnDefinition = "TEXT")
    var akResultJson: String? = null,
    @Column(columnDefinition = "TEXT")
    var guidelinesResultJson: String? = null,
    @Column(columnDefinition = "TEXT")
    var errorMessage: String? = null,
    @Column(length = 64)
    var diffBaseSha: String? = null,
    @Column(length = 64)
    var diffStartSha: String? = null,
    @Column(length = 64)
    var diffHeadSha: String? = null,
    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    var completedAt: LocalDateTime? = null,
    @Column(nullable = false, columnDefinition = "boolean default false")
    var integrationTestEnabled: Boolean = false,
    @Column(length = 16)
    var integrationTestStatus: String? = null,
    @Column(columnDefinition = "TEXT")
    var integrationTestLog: String? = null,
    @Column(columnDefinition = "TEXT")
    var integrationTestResult: String? = null,
)
