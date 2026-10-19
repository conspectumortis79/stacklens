package com.stacklens.domain.model.knowledge

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "knowledge_chunks")
class KnowledgeChunk(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "stable_id", nullable = false, unique = true, length = 36)
    var stableId: String = UUID.randomUUID().toString(),
    @Column(name = "document_id", nullable = false)
    var documentId: Long,
    @Column(name = "knowledge_base_id", nullable = false)
    var knowledgeBaseId: Long,
    @Column(nullable = false)
    var ordinal: Int,
    @Column(name = "section_title", length = 500)
    var sectionTitle: String? = null,
    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    var content: String,
    @Column(name = "content_hash", nullable = false, length = 64)
    var contentHash: String,
    @Column(name = "embedding_profile", length = 1000)
    var embeddingProfile: String? = null,
    @Column(name = "embedding_dimension")
    var embeddingDimension: Int? = null,
    @Lob
    @Column(
        name = "embedding_blob",
        columnDefinition = "VARBINARY(16777216)",
    )
    var embeddingBlob: ByteArray? = null,
    @Column(name = "embedding_norm")
    var embeddingNorm: Double? = null,
    @Column(name = "token_estimate", nullable = false)
    var tokenEstimate: Int = 0,
    @Lob
    @Column(name = "metadata_json", columnDefinition = "TEXT")
    var metadataJson: String? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
