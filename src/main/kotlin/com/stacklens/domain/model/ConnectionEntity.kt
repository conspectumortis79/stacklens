package com.stacklens.domain.model

sealed interface ConnectionEntity {
    val id: Long
    var name: String
    var active: Boolean
    var consecutiveFailures: Int
    var lastErrorAt: java.time.LocalDateTime?
    var lastErrorMessage: String?
}
