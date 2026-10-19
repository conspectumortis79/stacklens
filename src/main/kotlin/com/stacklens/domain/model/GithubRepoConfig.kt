package com.stacklens.domain.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "github_repo_configs")
class GithubRepoConfig(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    override val id: Long = 0,
    @Column(nullable = false)
    override var name: String,
    @Column(nullable = false)
    var owner: String,
    @Column(nullable = false)
    var repo: String,
    @Column(length = 255)
    var branch: String? = null,
    @Column(columnDefinition = "TEXT")
    var accessToken: String? = null,
    @Column(length = 512)
    var apiBase: String? = null,
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
