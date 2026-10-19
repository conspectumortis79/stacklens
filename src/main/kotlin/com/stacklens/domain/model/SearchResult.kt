package com.stacklens.domain.model
import com.stacklens.domain.enums.ResultType
import com.stacklens.domain.enums.SourceType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(
    name = "search_results",
    indexes = [
        Index(columnList = "sessionId"),
        Index(columnList = "keyword"),
        Index(columnList = "sourceType"),
    ],
)
class SearchResult(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(nullable = false)
    val sessionId: Long,
    @Column(nullable = false)
    val keyword: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val sourceType: SourceType,
    @Enumerated(EnumType.STRING)
    val resultType: ResultType? = null,
    @Column(length = 1024)
    val repoName: String? = null,
    @Column(length = 2048)
    val repoUrl: String? = null,
    val projectId: Long? = null,
    @Column(length = 2048)
    val filePath: String? = null,
    @Column(length = 512)
    val branch: String? = null,
    val lineNumber: Int? = null,
    val className: String? = null,
    val functionName: String? = null,
    @Column(columnDefinition = "TEXT")
    val codeSnippet: String? = null,
    val configName: String? = null,
    val dbHost: String? = null,
    val databaseName: String? = null,
    val schemaName: String? = null,
    val tableName: String? = null,
    val columnName: String? = null,
    val dataType: String? = null,
    @Column(columnDefinition = "TEXT")
    val ddlContext: String? = null,
    val crossRefParentId: Long? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
