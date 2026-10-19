package com.stacklens.domain.model.security

import com.stacklens.domain.enums.FileChangeType

data class SecurityFixResult(
    val prUrl: String?,
    val branchName: String?,
    val explanation: String,
    val filesChanged: List<String>,
    val isLocalRepo: Boolean,
    val localChanges: List<SecurityLocalFileChange>?,
)

data class SecurityLocalFileChange(
    val path: String,
    val code: String,
    val changeType: FileChangeType,
)
