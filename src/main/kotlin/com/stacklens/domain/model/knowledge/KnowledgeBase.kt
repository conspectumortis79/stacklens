package com.stacklens.domain.model.knowledge

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "knowledge_bases")
class KnowledgeBase(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "stable_id", nullable = false, unique = true, length = 36)
    var stableId: String = UUID.randomUUID().toString(),
    @Column(nullable = false, length = 200)
    var name: String,
    @Column(columnDefinition = "TEXT")
    var description: String = "",
    @Column(nullable = false)
    var active: Boolean = true,
    @Column(name = "include_in_global_chat", nullable = false)
    var includeInGlobalChat: Boolean = true,
    @Column(name = "system_managed", nullable = false)
    var systemManaged: Boolean = false,
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
)
