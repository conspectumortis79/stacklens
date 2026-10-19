package com.stacklens.domain.model
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "jira_configs")
class JiraConfig(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    override val id: Long = 0,
    @Column(nullable = false)
    override var name: String,
    @Column(nullable = false)
    var baseUrl: String,
    @Column(nullable = false)
    var projectKey: String = "",
    @Column(nullable = false)
    var username: String = "",
    @Column(nullable = false, columnDefinition = "TEXT")
    var apiToken: String,
    var cloudMode: Boolean = true,
    @Column(nullable = false, columnDefinition = "TEXT")
    var commentVisibilityRole: String = "",
    @Column(nullable = false)
    override var active: Boolean = true,
    @Column(
        name = "consecutive_failures",
        nullable = false,
        columnDefinition = "INTEGER NOT NULL DEFAULT 0",
    )
    override var consecutiveFailures: Int = 0,
    @Column(name = "last_error_at")
    override var lastErrorAt: LocalDateTime? = null,
    @Column(name = "story_points_field_id", columnDefinition = "TEXT NOT NULL DEFAULT ''")
    var storyPointsFieldId: String = "",
    @Column(name = "last_error_message", columnDefinition = "TEXT")
    override var lastErrorMessage: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
) : ConnectionEntity
