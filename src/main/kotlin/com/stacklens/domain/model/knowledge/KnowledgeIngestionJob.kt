package com.stacklens.domain.model.knowledge

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "knowledge_ingestion_jobs")
class KnowledgeIngestionJob(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "source_id")
    var sourceId: Long? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: KnowledgeJobStatus = KnowledgeJobStatus.QUEUED,
    @Column(nullable = false, length = 200)
    var phase: String = "queued",
    @Column(name = "processed_documents", nullable = false)
    var processedDocuments: Int = 0,
    @Column(name = "total_documents", nullable = false)
    var totalDocuments: Int = 0,
    @Column(name = "processed_chunks", nullable = false)
    var processedChunks: Int = 0,
    @Column(name = "total_chunks", nullable = false)
    var totalChunks: Int = 0,
    @Column(name = "error_message", columnDefinition = "TEXT")
    var errorMessage: String? = null,
    @Column(name = "started_at")
    var startedAt: LocalDateTime? = null,
    @Column(name = "finished_at")
    var finishedAt: LocalDateTime? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
