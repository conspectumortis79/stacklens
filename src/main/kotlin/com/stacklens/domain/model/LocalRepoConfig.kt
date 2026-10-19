package com.stacklens.domain.model
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "local_repo_configs")
class LocalRepoConfig(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(nullable = false)
    var name: String,
    @Column(nullable = false, columnDefinition = "TEXT")
    var path: String,
    var active: Boolean = true,
    @Column(nullable = false, length = 30)
    var scope: String = "REPO_ANALYSIS",
)
