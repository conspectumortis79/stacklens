
package com.stacklens.domain.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.net.URI

@Entity
@Table(name = "gitlab_configs")
class GitlabConfig(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    override val id: Long = 0,
    @Column(nullable = false)
    override var name: String,
    @Column(nullable = false)
    var baseUrl: String,
    @Column(nullable = false)
    var accessToken: String,
    var groupId: String? = null,
    @Column(columnDefinition = "TEXT")
    var projectIds: String? = null,
    var defaultBranch: String? = null,
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
) : ConnectionEntity {
    fun projectSelectorList(): List<String> = projectIds?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()

    fun projectIdList(): List<Long> = projectSelectorList().mapNotNull { it.toLongOrNull() }

    fun resolvedUrls(): Pair<String, String?> =
        try {
            val uri = URI(baseUrl.trimEnd('/'))
            val apiBase = "${uri.scheme}://${uri.authority}"
            val pathGroup = uri.path?.let { it.trimStart('/').takeIf { p -> p.isNotBlank() } }
            val effectiveGroup = groupId?.takeIf { it.isNotBlank() } ?: pathGroup
            Pair(apiBase, effectiveGroup)
        } catch (_: Exception) {
            Pair(baseUrl.trimEnd('/'), groupId?.takeIf { it.isNotBlank() })
        }
}
