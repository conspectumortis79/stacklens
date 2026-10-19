package com.stacklens.domain.model.knowledge

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Transient
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "knowledge_sources")
class KnowledgeSource(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "stable_id", nullable = false, unique = true, length = 36)
    var stableId: String = UUID.randomUUID().toString(),
    @Column(name = "knowledge_base_id", nullable = false)
    var knowledgeBaseId: Long,
    @Column(nullable = false, length = 200)
    var name: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    var type: KnowledgeSourceType,
    @Column(nullable = false)
    var active: Boolean = true,
    @Column(name = "root_url", columnDefinition = "TEXT")
    var rootUrl: String? = null,
    @Column(name = "external_config_id")
    var externalConfigId: Long? = null,
    @Column(name = "external_reference", length = 500)
    var externalReference: String? = null,
    @Column(name = "include_children", nullable = false)
    var includeChildren: Boolean = false,
    @Column(name = "include_comments", nullable = false)
    var includeComments: Boolean = false,
    @Column(name = "max_depth", nullable = false)
    var maxDepth: Int = 10,
    @Column(name = "include_linked_tickets", nullable = false, columnDefinition = "BOOLEAN NOT NULL DEFAULT TRUE")
    var includeLinkedTickets: Boolean = true,
    @Column(name = "include_parent", nullable = false, columnDefinition = "BOOLEAN NOT NULL DEFAULT FALSE")
    var includeParent: Boolean = false,
    @Column(name = "auto_managed", nullable = false)
    var autoManaged: Boolean = false,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: KnowledgeSourceStatus = KnowledgeSourceStatus.PENDING,
    @Column(name = "error_message", columnDefinition = "TEXT")
    var errorMessage: String? = null,
    @Column(name = "last_sync_at")
    var lastSyncAt: LocalDateTime? = null,
    @Column(name = "last_successful_sync_at")
    var lastSuccessfulSyncAt: LocalDateTime? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
    @Transient
    var totalBytes: Long? = null,
)
