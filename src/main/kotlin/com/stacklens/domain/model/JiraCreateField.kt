package com.stacklens.domain.model

data class JiraCreateField(
    val fieldId: String,
    val name: String,
    val multiple: Boolean,
    val allowedValues: List<String>,
)
