package com.stacklens.domain.model.knowledge

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "knowledge_documents")
class KnowledgeDocument(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "stable_id", nullable = false, unique = true, length = 36)
    var stableId: String = UUID.randomUUID().toString(),
    @Column(name = "source_id", nullable = false)
    var sourceId: Long,
    @Column(name = "knowledge_base_id", nullable = false)
    var knowledgeBaseId: Long,
    @Column(name = "external_id", length = 500)
    var externalId: String? = null,
    @Column(nullable = false, length = 500)
    var title: String,
    @Column(name = "source_uri", columnDefinition = "TEXT")
    var sourceUri: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 40)
    var sourceType: KnowledgeSourceType,
    @Column(name = "mime_type", length = 150)
    var mimeType: String? = null,
    @Lob
    @Column(name = "plain_text", nullable = false, columnDefinition = "TEXT")
    var plainText: String,
    @Column(name = "content_hash", nullable = false, length = 64)
    var contentHash: String,
    @Lob
    @Column(name = "metadata_json", columnDefinition = "TEXT")
    var metadataJson: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: KnowledgeDocumentStatus = KnowledgeDocumentStatus.PENDING,
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
    @Column(name = "indexed_at")
    var indexedAt: LocalDateTime? = null,
)
