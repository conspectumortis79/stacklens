package com.stacklens.domain.enums

enum class ConnectionKind(
    val id: String,
    val displayName: String,
) {
    DATABASE("database", "Datenbank"),
    JIRA("jira", "Jira"),
    CONFLUENCE("confluence", "Confluence"),
    GITLAB("gitlab", "GitLab"),
    GITHUB("github", "GitHub"),
    GRAFANA("grafana", "Grafana"),
    MCP("mcp", "MCP-Server"),
}
