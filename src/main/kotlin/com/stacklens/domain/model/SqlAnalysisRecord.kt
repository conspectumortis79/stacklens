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
@Table(name = "sql_analyses", indexes = [Index(columnList = "createdAt")])
class SqlAnalysisRecord(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(nullable = false, columnDefinition = "TEXT")
    val sql: String,
    @Column(columnDefinition = "TEXT")
    val ddl: String? = null,
    @Column(columnDefinition = "TEXT")
    val testData: String? = null,
    @Column(columnDefinition = "TEXT")
    val analysisResult: String? = null,
    val status: String = "RUNNING",
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
