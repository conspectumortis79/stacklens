package com.stacklens.domain.model.knowledge

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
    name = "knowledge_index_generations",
    indexes = [Index(columnList = "active")],
)
class KnowledgeIndexGeneration(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "knowledge_base_id", nullable = false)
    var knowledgeBaseId: Long,
    @Column(name = "generation_id", nullable = false, length = 64)
    var generationId: String,
    @Column(name = "embedding_profile", nullable = false, length = 1000)
    var embeddingProfile: String,
    @Column(name = "active", nullable = false)
    var active: Boolean = false,
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    @Column(name = "activated_at")
    var activatedAt: LocalDateTime? = null,
)
