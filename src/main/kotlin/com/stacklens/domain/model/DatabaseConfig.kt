
package com.stacklens.domain.model

import com.stacklens.domain.enums.DatabaseType
import com.stacklens.domain.splitCsvList
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
@Table(name = "database_configs")
open class DatabaseConfig(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    override val id: Long = 0,
    @Column(nullable = false)
    override var name: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var dbType: DatabaseType,
    @Column(nullable = false)
    var host: String,
    @Column(nullable = false)
    var port: Int,
    @Column(nullable = false)
    var databaseName: String,
    @Column(nullable = false)
    var username: String,
    @Column(nullable = false)
    var password: String,
    @Column(columnDefinition = "TEXT")
    var schemas: String? = null,
    override var active: Boolean = true,
    @Column(
        name = "consecutive_failures",
        nullable = false,
        columnDefinition = "INTEGER NOT NULL DEFAULT 0",
    )
    override var consecutiveFailures: Int = 0,
    @Column(name = "last_error_at")
    override var lastErrorAt: LocalDateTime? = null,
    @Column(name = "last_error_message", columnDefinition = "TEXT")
    override var lastErrorMessage: String? = null,
) : ConnectionEntity {
    fun schemaList(): List<String> = schemas.splitCsvList()
}

data class DatabaseConnectionError(
    val configId: Long,
    val configName: String,
    val host: String,
    val databaseName: String,
    val dbType: String,
    val errorMessage: String,
)
