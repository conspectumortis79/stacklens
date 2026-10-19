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
@Table(name = "confluence_tools", indexes = [Index(columnList = "createdAt")])
class ConfluenceTool(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(nullable = false)
    var name: String,
    @Column(columnDefinition = "TEXT")
    var description: String? = null,
    @Column(nullable = false, columnDefinition = "TEXT")
    var confluencePageUrl: String,
    var confluenceConfigId: Long? = null,
    @Column(nullable = false)
    var status: String = "PENDING",
    var currentStep: String? = null,
    @Column(columnDefinition = "TEXT")
    var pageContentText: String? = null,
    @Column(columnDefinition = "TEXT")
    var generationPrompt: String? = null,
    @Column(columnDefinition = "TEXT")
    var generatedCode: String? = null,
    @Column(columnDefinition = "TEXT")
    var jarPath: String? = null,
    @Column(columnDefinition = "TEXT")
    var lastRunOutput: String? = null,
    var lastRunAt: LocalDateTime? = null,
    @Column(columnDefinition = "TEXT")
    var errorMessage: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    var completedAt: LocalDateTime? = null,
    @Column(nullable = false)
    var hiddenFromHistory: Boolean = false,
    var icon: String? = null,
)
