package com.stacklens.application.port.out.grafana

data class GrafanaConfigInfo(
    val id: Long,
    val name: String,
    val baseUrl: String,
    val username: String,
    val password: String,
    val apiToken: String,
    val orgId: Long,
)
