package com.stacklens.domain.pipeline

data class PipelineMrResult(
    val provider: String,
    val url: String,
    val kind: String,
    val branch: String?,
)
