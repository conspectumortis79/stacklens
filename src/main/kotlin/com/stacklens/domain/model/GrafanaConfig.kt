package com.stacklens.domain.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "grafana_configs")
class GrafanaConfig(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    override val id: Long = 0,
    @Column(nullable = false)
    override var name: String,
    @Column(nullable = false)
    var baseUrl: String,
    @Column(nullable = false)
    var username: String,
    @Column(nullable = false)
    var password: String,
    @Column(nullable = false)
    var apiToken: String = "",
    var orgId: Long = 1,
    var scanPanelQueries: Boolean = true,
    override var active: Boolean = true,
    @Column(
        name = "consecutive_failures",
        nullable = false,
        columnDefinition = "INTEGER NOT NULL DEFAULT 0",
    )
    override var consecutiveFailures: Int = 0,
    @Column(name = "last_error_at")
    override var lastErrorAt: java.time.LocalDateTime? = null,
    @Column(name = "last_error_message", columnDefinition = "TEXT")
    override var lastErrorMessage: String? = null,
) : ConnectionEntity
