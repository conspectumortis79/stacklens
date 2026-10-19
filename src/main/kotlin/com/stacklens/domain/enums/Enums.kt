package com.stacklens.domain.enums

enum class SourceType(
    val label: String,
) {
    GITLAB("GitLab"),
    DATABASE("Database"),
    PIPELINE("CI/CD Pipeline"),
    CONFLUENCE("Confluence"),
    GRAFANA("Grafana"),
    JIRA("Jira"),
    CONTEXT_MAP("Context Map"),
    GITHUB("GitHub"),
}

enum class ResultType(
    val label: String,
) {
    FILE("File"),
    CLASS("Class"),
    FUNCTION("Function"),
    TABLE("Table"),
    COLUMN("Column"),
    VIEW("View"),
    PROCEDURE("Procedure/Function"),
    INDEX("Index"),
    TRIGGER("Trigger"),
    SEQUENCE("Sequence"),
    PIPELINE_JOB("Pipeline-Job"),
    PIPELINE_STAGE("Pipeline-Stage"),
    PIPELINE_SCRIPT("Pipeline-Script"),
    CONFLUENCE_PAGE("Confluence Page"),
    CONFLUENCE_BLOGPOST("Confluence-Blogpost"),
    GRAFANA_DASHBOARD("Grafana-Dashboard"),
    GRAFANA_PANEL("Grafana-Panel"),
    JIRA_ISSUE("Jira-Issue"),
    JIRA_BUG("Jira-Bug"),
    JIRA_STORY("Jira-Story"),
    JIRA_TASK("Jira-Task"),
    JIRA_EPIC("Jira-Epic"),
    CONTEXT_MAP_FILE("Context Map File"),
    CONTEXT_MAP_COMPONENT("Context Map Component"),
}

enum class SessionStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
}

enum class DatabaseType(
    val driverClass: String,
    val defaultPort: Int,
) {
    POSTGRESQL("org.postgresql.Driver", 5432),
    MYSQL("com.mysql.cj.jdbc.Driver", 3306),
    MSSQL("com.microsoft.sqlserver.jdbc.SQLServerDriver", 1433),
}
