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
    name = "jira_ticket_summaries",
    indexes = [Index(columnList = "createdAt"), Index(columnList = "ticketKey")],
)
class JiraTicketSummary(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(nullable = false)
    var ticketKey: String,
    var jiraConfigId: Long? = null,
    @Column(columnDefinition = "TEXT")
    var templateOverride: String? = null,
    @Column(nullable = false)
    var status: String = "PENDING",
    var currentStep: String? = null,
    @Column(columnDefinition = "TEXT")
    var errorMessage: String? = null,
    @Column(columnDefinition = "TEXT")
    var originalText: String? = null,
    @Column(columnDefinition = "TEXT")
    var summaryMarkdown: String? = null,
    @Column(columnDefinition = "TEXT")
    var summaryHtml: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    var completedAt: LocalDateTime? = null,
)
