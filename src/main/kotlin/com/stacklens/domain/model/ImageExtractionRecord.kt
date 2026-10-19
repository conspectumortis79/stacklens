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
@Table(name = "image_extractions", indexes = [Index(columnList = "createdAt")])
class ImageExtractionRecord(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(nullable = false, columnDefinition = "TEXT")
    val originalFileName: String,
    @Column(nullable = false, columnDefinition = "TEXT")
    val sourcePath: String,
    @Column(nullable = false, columnDefinition = "TEXT")
    val mimeType: String,
    val fileSizeBytes: Long = 0,
    @Column(columnDefinition = "TEXT")
    val extractedText: String? = null,
    @Column(columnDefinition = "TEXT")
    val outputFilePath: String? = null,
    val status: String = "RUNNING",
    @Column(columnDefinition = "TEXT")
    val errorMessage: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
