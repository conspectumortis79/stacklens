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
@Table(name = "ai_analyses", indexes = [Index(columnList = "sessionId, createdAt")])
class AiAnalysis(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(nullable = false)
    val sessionId: Long,
    @Column(nullable = false)
    var status: String = "PENDING",
    @Column(columnDefinition = "TEXT")
    var analysisText: String? = null,
    @Column(columnDefinition = "TEXT")
    var drawioXml: String? = null,
    @Column(columnDefinition = "TEXT")
    var mermaid: String? = null,
    var modelUsed: String? = null,
    var inputTokens: Int? = null,
    var outputTokens: Int? = null,
    @Column(columnDefinition = "TEXT")
    var errorMessage: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    var completedAt: LocalDateTime? = null,
)
