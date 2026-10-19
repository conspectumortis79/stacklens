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
@Table(name = "ticket_creation_records", indexes = [Index(columnList = "createdAt")])
class TicketCreationRecord(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(columnDefinition = "TEXT")
    val inputText: String = "",
    @Column(columnDefinition = "TEXT")
    val summary: String? = null,
    val issueType: String? = null,
    @Column(columnDefinition = "TEXT")
    val labels: String? = null,
    @Column(columnDefinition = "TEXT")
    val resultJson: String? = null,
    val status: String = "COMPLETED",
    @Column(columnDefinition = "TEXT")
    val errorMessage: String? = null,
    val jiraConfigId: Long? = null,
    val jiraProjectKey: String? = null,
    val jiraKey: String? = null,
    @Column(columnDefinition = "TEXT")
    val jiraUrl: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
) {
    fun toMap(): Map<String, Any?> =
        mapOf(
            "id" to id,
            "inputText" to inputText,
            "summary" to summary,
            "issueType" to issueType,
            "labels" to labels,
            "resultJson" to resultJson,
            "status" to status,
            "errorMessage" to errorMessage,
            "jiraConfigId" to jiraConfigId,
            "jiraProjectKey" to jiraProjectKey,
            "jiraKey" to jiraKey,
            "jiraUrl" to jiraUrl,
            "createdAt" to createdAt.toString(),
        )
}
