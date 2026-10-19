package com.stacklens.infrastructure.db

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import javax.sql.DataSource

@Component
@Lazy(false)
class SchemaUpgradeRunner(
    private val dataSource: DataSource,
) {
    private val log = LoggerFactory.getLogger(SchemaUpgradeRunner::class.java)

    @PostConstruct
    fun upgrade() {
        try {
            runUpgrade()
        } catch (e: Exception) {
            log.error("[SchemaUpgrade] could not obtain DB connection — schema upgrade skipped: {}", e.message)
        }
    }

    private fun runUpgrade() {
        val statements =
            listOf(
                "ALTER TABLE IF EXISTS sprint_preparations RENAME TO ticket_refinements",
                "ALTER TABLE IF EXISTS jira_ticket_analyses RENAME TO jira_ticket_implementations",
                "ALTER TABLE mcp_server_configs ADD COLUMN IF NOT EXISTS route_grafana BOOLEAN NOT NULL DEFAULT TRUE",
                "ALTER TABLE mcp_server_configs ADD COLUMN IF NOT EXISTS grafana_base_url TEXT NOT NULL DEFAULT ''",
                "ALTER TABLE mcp_server_configs ADD COLUMN IF NOT EXISTS grafana_api_key TEXT NOT NULL DEFAULT ''",
                "ALTER TABLE ticket_refinements ADD COLUMN IF NOT EXISTS repo_selections_json TEXT",
                "ALTER TABLE ticket_refinements ADD COLUMN IF NOT EXISTS single_ticket_only BOOLEAN NOT NULL DEFAULT TRUE",
                "ALTER TABLE jira_configs ADD COLUMN IF NOT EXISTS comment_visibility_role TEXT NOT NULL DEFAULT ''",
                "ALTER TABLE confluence_tools ADD COLUMN IF NOT EXISTS hidden_from_history BOOLEAN NOT NULL DEFAULT FALSE",
                "ALTER TABLE confluence_tools ADD COLUMN IF NOT EXISTS icon TEXT",
                "ALTER TABLE search_sessions ADD COLUMN IF NOT EXISTS include_github BOOLEAN NOT NULL DEFAULT FALSE",
                "ALTER TABLE search_sessions ADD COLUMN IF NOT EXISTS github_config_ids TEXT",
                "ALTER TABLE github_repo_configs ADD COLUMN IF NOT EXISTS api_base VARCHAR(512)",
                "ALTER TABLE database_configs  ADD COLUMN IF NOT EXISTS consecutive_failures INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE database_configs  ADD COLUMN IF NOT EXISTS last_error_at TIMESTAMP NULL",
                "ALTER TABLE database_configs  ADD COLUMN IF NOT EXISTS last_error_message TEXT NULL",
                "ALTER TABLE jira_configs     ADD COLUMN IF NOT EXISTS consecutive_failures INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE jira_configs     ADD COLUMN IF NOT EXISTS last_error_at TIMESTAMP NULL",
                "ALTER TABLE jira_configs     ADD COLUMN IF NOT EXISTS last_error_message TEXT NULL",
                "ALTER TABLE confluence_configs ADD COLUMN IF NOT EXISTS consecutive_failures INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE confluence_configs ADD COLUMN IF NOT EXISTS last_error_at TIMESTAMP NULL",
                "ALTER TABLE confluence_configs ADD COLUMN IF NOT EXISTS last_error_message TEXT NULL",
                "ALTER TABLE gitlab_configs  ADD COLUMN IF NOT EXISTS consecutive_failures INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE gitlab_configs  ADD COLUMN IF NOT EXISTS last_error_at TIMESTAMP NULL",
                "ALTER TABLE gitlab_configs  ADD COLUMN IF NOT EXISTS last_error_message TEXT NULL",
                "ALTER TABLE grafana_configs ADD COLUMN IF NOT EXISTS consecutive_failures INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE grafana_configs ADD COLUMN IF NOT EXISTS last_error_at TIMESTAMP NULL",
                "ALTER TABLE grafana_configs ADD COLUMN IF NOT EXISTS last_error_message TEXT NULL",
                "ALTER TABLE github_repo_configs ADD COLUMN IF NOT EXISTS consecutive_failures INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE github_repo_configs ADD COLUMN IF NOT EXISTS last_error_at TIMESTAMP NULL",
                "ALTER TABLE github_repo_configs ADD COLUMN IF NOT EXISTS last_error_message TEXT NULL",
                "ALTER TABLE mcp_server_configs ADD COLUMN IF NOT EXISTS consecutive_failures INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE mcp_server_configs ADD COLUMN IF NOT EXISTS last_error_at TIMESTAMP NULL",
                "ALTER TABLE mcp_server_configs ADD COLUMN IF NOT EXISTS last_error_message TEXT NULL",
                "ALTER TABLE database_configs  DROP COLUMN IF EXISTS auto_disabled",
                "ALTER TABLE database_configs  DROP COLUMN IF EXISTS inactive_since",
                "ALTER TABLE jira_configs      DROP COLUMN IF EXISTS auto_disabled",
                "ALTER TABLE jira_configs      DROP COLUMN IF EXISTS inactive_since",
                "ALTER TABLE confluence_configs DROP COLUMN IF EXISTS auto_disabled",
                "ALTER TABLE confluence_configs DROP COLUMN IF EXISTS inactive_since",
                "ALTER TABLE gitlab_configs    DROP COLUMN IF EXISTS auto_disabled",
                "ALTER TABLE gitlab_configs    DROP COLUMN IF EXISTS inactive_since",
                "ALTER TABLE grafana_configs   DROP COLUMN IF EXISTS auto_disabled",
                "ALTER TABLE grafana_configs   DROP COLUMN IF EXISTS inactive_since",
                "ALTER TABLE github_repo_configs DROP COLUMN IF EXISTS auto_disabled",
                "ALTER TABLE github_repo_configs DROP COLUMN IF EXISTS inactive_since",
                "ALTER TABLE mcp_server_configs DROP COLUMN IF EXISTS auto_disabled",
                "ALTER TABLE mcp_server_configs DROP COLUMN IF EXISTS inactive_since",
                "ALTER TABLE confluence_configs DROP COLUMN IF EXISTS max_results",
                "ALTER TABLE IF EXISTS knowledge_sources ALTER COLUMN type ENUM('CONFLUENCE', 'FILE', 'JIRA', 'JIRA_SPRINT', 'MANUAL', 'REVIEW_ANALYSIS', 'SPRINT_REVIEW', 'TICKET_CREATION', 'TICKET_REFINEMENT', 'TICKET_SUMMARY')",
                "ALTER TABLE IF EXISTS knowledge_documents ALTER COLUMN source_type ENUM('CONFLUENCE', 'FILE', 'JIRA', 'JIRA_SPRINT', 'MANUAL', 'REVIEW_ANALYSIS', 'SPRINT_REVIEW', 'TICKET_CREATION', 'TICKET_REFINEMENT', 'TICKET_SUMMARY')",
                "ALTER TABLE knowledge_sources ADD COLUMN IF NOT EXISTS include_linked_tickets BOOLEAN NOT NULL DEFAULT TRUE",
                "ALTER TABLE knowledge_sources ADD COLUMN IF NOT EXISTS include_parent BOOLEAN NOT NULL DEFAULT FALSE",
            )
        dataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                statements.forEach { sql ->
                    try {
                        stmt.execute(sql)
                        log.debug("[SchemaUpgrade] applied: {}", sql)
                    } catch (e: Exception) {
                        log.error("[SchemaUpgrade] failed '{}': {}", sql, e.message)
                    }
                }
            }
        }
        log.info("[SchemaUpgrade] schema upgrade complete")
    }
}
