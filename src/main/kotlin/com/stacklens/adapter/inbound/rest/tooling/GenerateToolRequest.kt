package com.stacklens.adapter.inbound.rest.tooling

data class GenerateToolRequest(
    val name: String = "",
    val scriptContent: String? = null,
)
