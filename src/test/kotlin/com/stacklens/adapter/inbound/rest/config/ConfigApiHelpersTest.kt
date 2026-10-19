package com.stacklens.adapter.inbound.rest.config

import com.stacklens.domain.enums.DatabaseType
import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.domain.model.DatabaseConfig
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.GrafanaConfig
import com.stacklens.domain.model.JiraConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConfigApiHelpersTest {
    @Test
    fun `loadMutateSave reuses the found entity and applies a non-blank secret`() {
        val existing = GitlabConfig(id = 5L, name = "old", baseUrl = "https://old", accessToken = "stored")

        val saved =
            loadMutateSave(
                id = 5L,
                find = { existing },
                create = { GitlabConfig(name = "", baseUrl = "", accessToken = "") },
                secrets = listOf("fresh" to GitlabConfig::accessToken),
                save = { it },
            ) {
                name = "new"
            }

        assertEquals("new", saved.name)
        assertEquals("fresh", saved.accessToken)
    }

    @Test
    fun `loadMutateSave keeps the stored secret when the submitted secret is blank`() {
        val existing = GitlabConfig(id = 5L, name = "old", baseUrl = "https://old", accessToken = "stored")

        val saved =
            loadMutateSave(
                id = 5L,
                find = { existing },
                create = { GitlabConfig(name = "", baseUrl = "", accessToken = "") },
                secrets = listOf("" to GitlabConfig::accessToken),
                save = { it },
            ) {
                name = "new"
            }

        assertEquals("stored", saved.accessToken)
    }

    @Test
    fun `loadMutateSave creates a new entity when the id is absent`() {
        val saved =
            loadMutateSave(
                id = null,
                find = { error("must not look up an entity without id") },
                create = { GitlabConfig(name = "created", baseUrl = "https://new", accessToken = "") },
                secrets = listOf("" to GitlabConfig::accessToken),
                save = { it },
            ) {
                name = "renamed"
            }

        assertEquals("renamed", saved.name)
        assertEquals("", saved.accessToken)
    }

    @Test
    fun `GitlabConfig toDto masks accessToken`() {
        val config = GitlabConfig(id = 1L, name = "gl", baseUrl = "https://gl.example.com", accessToken = "secret")
        val dto = config.toDto()
        assertEquals("", dto.accessToken)
        assertTrue(dto.accessTokenSet)
    }

    @Test
    fun `GitlabConfig toDto sets accessTokenSet false when token is blank`() {
        val config = GitlabConfig(id = 2L, name = "gl", baseUrl = "https://gl.example.com", accessToken = "")
        val dto = config.toDto()
        assertFalse(dto.accessTokenSet)
    }

    @Test
    fun `GitlabConfig toExportDto includes actual accessToken`() {
        val config = GitlabConfig(id = 1L, name = "gl", baseUrl = "https://gl.example.com", accessToken = "tok")
        val dto = config.toExportDto()
        assertEquals("tok", dto.accessToken)
    }

    @Test
    fun `DatabaseConfig toDto masks password`() {
        val config =
            DatabaseConfig(
                id = 1L,
                name = "db",
                dbType = DatabaseType.POSTGRESQL,
                host = "localhost",
                port = 5432,
                databaseName = "mydb",
                username = "user",
                password = "secret",
            )
        val dto = config.toDto()
        assertEquals("", dto.password)
        assertTrue(dto.passwordSet)
    }

    @Test
    fun `DatabaseConfig toDto sets passwordSet false when password is blank`() {
        val config =
            DatabaseConfig(
                id = 2L,
                name = "db",
                dbType = DatabaseType.POSTGRESQL,
                host = "localhost",
                port = 5432,
                databaseName = "mydb",
                username = "user",
                password = "",
            )
        val dto = config.toDto()
        assertFalse(dto.passwordSet)
    }

    @Test
    fun `ConfluenceConfig toDto masks apiToken`() {
        val config = ConfluenceConfig(name = "cf", baseUrl = "https://cf.example.com", username = "user", apiToken = "tok123")
        val dto = config.toDto()
        assertEquals("", dto.apiToken)
        assertTrue(dto.apiTokenSet)
    }

    @Test
    fun `ConfluenceConfig toDto sets apiTokenSet false when token is blank`() {
        val config = ConfluenceConfig(name = "cf", baseUrl = "https://cf.example.com", username = "user", apiToken = "")
        val dto = config.toDto()
        assertFalse(dto.apiTokenSet)
    }

    @Test
    fun `GrafanaConfig toDto masks password and apiToken`() {
        val config =
            GrafanaConfig(name = "gr", baseUrl = "https://grafana.example.com", username = "admin", password = "pass", apiToken = "gtoken")
        val dto = config.toDto()
        assertEquals("", dto.password)
        assertTrue(dto.passwordSet)
        assertEquals("", dto.apiToken)
        assertTrue(dto.apiTokenSet)
    }

    @Test
    fun `GrafanaConfig toDto sets passwordSet false when password is blank`() {
        val config = GrafanaConfig(name = "gr", baseUrl = "https://grafana.example.com", username = "admin", password = "", apiToken = "")
        val dto = config.toDto()
        assertFalse(dto.passwordSet)
        assertFalse(dto.apiTokenSet)
    }

    @Test
    fun `JiraConfig toDto masks apiToken`() {
        val config =
            JiraConfig(name = "jira", baseUrl = "https://jira.example.com", projectKey = "PROJ", username = "user", apiToken = "jtoken")
        val dto = config.toDto()
        assertEquals("", dto.apiToken)
        assertTrue(dto.apiTokenSet)
    }

    @Test
    fun `JiraConfig toDto sets apiTokenSet false when token is blank`() {
        val config = JiraConfig(name = "jira", baseUrl = "https://jira.example.com", projectKey = "PROJ", username = "user", apiToken = "")
        val dto = config.toDto()
        assertFalse(dto.apiTokenSet)
    }

    @Test
    fun `GitlabConfig toExportDto sets accessTokenSet false when token is blank`() {
        val config = GitlabConfig(id = 1L, name = "gl", baseUrl = "https://gl.example.com", accessToken = "")
        val dto = config.toExportDto()
        assertEquals("", dto.accessToken)
        assertFalse(dto.accessTokenSet)
    }

    @Test
    fun `DatabaseConfig toExportDto includes password and sets passwordSet true`() {
        val config =
            DatabaseConfig(
                id = 1L,
                name = "db",
                dbType = DatabaseType.POSTGRESQL,
                host = "localhost",
                port = 5432,
                databaseName = "mydb",
                username = "user",
                password = "secret",
            )
        val dto = config.toExportDto()
        assertEquals("secret", dto.password)
        assertTrue(dto.passwordSet)
    }

    @Test
    fun `DatabaseConfig toExportDto sets passwordSet false when password is blank`() {
        val config =
            DatabaseConfig(
                id = 2L,
                name = "db",
                dbType = DatabaseType.POSTGRESQL,
                host = "localhost",
                port = 5432,
                databaseName = "mydb",
                username = "user",
                password = "",
            )
        val dto = config.toExportDto()
        assertFalse(dto.passwordSet)
    }

    @Test
    fun `ConfluenceConfig toExportDto includes apiToken and sets apiTokenSet true`() {
        val config = ConfluenceConfig(name = "cf", baseUrl = "https://cf.example.com", username = "user", apiToken = "tok123")
        val dto = config.toExportDto()
        assertEquals("tok123", dto.apiToken)
        assertTrue(dto.apiTokenSet)
    }

    @Test
    fun `ConfluenceConfig toExportDto sets apiTokenSet false when token is blank`() {
        val config = ConfluenceConfig(name = "cf", baseUrl = "https://cf.example.com", username = "user", apiToken = "")
        val dto = config.toExportDto()
        assertFalse(dto.apiTokenSet)
    }

    @Test
    fun `GrafanaConfig toExportDto includes password and apiToken with flags true`() {
        val config =
            GrafanaConfig(name = "gr", baseUrl = "https://grafana.example.com", username = "admin", password = "pass", apiToken = "gtoken")
        val dto = config.toExportDto()
        assertEquals("pass", dto.password)
        assertTrue(dto.passwordSet)
        assertEquals("gtoken", dto.apiToken)
        assertTrue(dto.apiTokenSet)
    }

    @Test
    fun `GrafanaConfig toExportDto sets flags false when password and apiToken are blank`() {
        val config =
            GrafanaConfig(name = "gr", baseUrl = "https://grafana.example.com", username = "admin", password = "", apiToken = "")
        val dto = config.toExportDto()
        assertFalse(dto.passwordSet)
        assertFalse(dto.apiTokenSet)
    }

    @Test
    fun `GrafanaConfig toExportDto mixed blank password non-blank apiToken`() {
        val config =
            GrafanaConfig(name = "gr", baseUrl = "https://grafana.example.com", username = "admin", password = "", apiToken = "tok")
        val dto = config.toExportDto()
        assertFalse(dto.passwordSet)
        assertTrue(dto.apiTokenSet)
    }

    @Test
    fun `GrafanaConfig toExportDto non-blank password blank apiToken`() {
        val config =
            GrafanaConfig(name = "gr", baseUrl = "https://grafana.example.com", username = "admin", password = "pass", apiToken = "")
        val dto = config.toExportDto()
        assertTrue(dto.passwordSet)
        assertFalse(dto.apiTokenSet)
    }

    @Test
    fun `JiraConfig toExportDto includes apiToken and sets apiTokenSet true`() {
        val config =
            JiraConfig(name = "jira", baseUrl = "https://jira.example.com", projectKey = "PROJ", username = "user", apiToken = "jtoken")
        val dto = config.toExportDto()
        assertEquals("jtoken", dto.apiToken)
        assertTrue(dto.apiTokenSet)
    }

    @Test
    fun `JiraConfig toExportDto sets apiTokenSet false when token is blank`() {
        val config =
            JiraConfig(name = "jira", baseUrl = "https://jira.example.com", projectKey = "PROJ", username = "user", apiToken = "")
        val dto = config.toExportDto()
        assertFalse(dto.apiTokenSet)
    }

    @Test
    fun `JiraConfig toDto and toExportDto round-trip commentVisibilityRole`() {
        val config =
            JiraConfig(
                name = "jira",
                baseUrl = "https://jira.example.com",
                projectKey = "PROJ",
                username = "user",
                apiToken = "tok",
            ).also { it.commentVisibilityRole = "Developers" }
        assertEquals("Developers", config.toDto().commentVisibilityRole)
        assertEquals("Developers", config.toExportDto().commentVisibilityRole)
    }
}
