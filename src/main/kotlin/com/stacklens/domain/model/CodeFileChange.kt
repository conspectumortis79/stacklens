package com.stacklens.domain.model

import com.stacklens.domain.enums.FileChangeType

data class CodeFileChange(
    val path: String = "",
    val changeType: FileChangeType = FileChangeType.UPDATE,
    val code: String = "",
)
