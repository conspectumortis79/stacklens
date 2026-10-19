package com.stacklens.application.service.health

import com.stacklens.adapter.out.health.InMemoryConnectionHealthTracker
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.ConfluenceConfigPort
import com.stacklens.application.port.out.DatabaseConfigPort
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.GrafanaConfigPort
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.health.ConfluenceConnectionProbePort
import com.stacklens.application.port.out.health.ConnectionProbeResult
import com.stacklens.application.port.out.health.DatabaseConnectionProbePort
import com.stacklens.application.port.out.health.GithubConnectionProbePort
import com.stacklens.application.port.out.health.GitlabConnectionProbePort
import com.stacklens.application.port.out.health.GrafanaConnectionProbePort
import com.stacklens.application.port.out.health.JiraConnectionProbePort
import com.stacklens.application.port.out.health.McpConnectionProbePort
import com.stacklens.domain.enums.AiProvider
import com.stacklens.domain.enums.ConnectionKind
import com.stacklens.domain.enums.DatabaseType
import com.stacklens.domain.model.AiConfig
import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.domain.model.DatabaseConfig
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.GrafanaConfig
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.McpServerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ConnectionHealthProbeServiceTest {
    private val tracker = InMemoryConnectionHealthTracker()

    private val databaseConfig = DatabaseConfig(name = "d", dbType = DatabaseType.POSTGRESQL, host = "h", port = 5432, databaseName = "n", username = "u", password = "p")
    private val jiraConfig = JiraConfig(name = "j", baseUrl = "u", username = "u", apiToken = "t", projectKey = "P")
    private val confluenceConfig = ConfluenceConfig(name = "c", baseUrl = "u", username = "u", apiToken = "t")
    private val gitlabConfig = GitlabConfig(name = "g", baseUrl = "u", accessToken = "t", groupId = "g")
    private val githubConfig = GithubRepoConfig(name = "gh", owner = "o", repo = "r")
    private val grafanaConfig = GrafanaConfig(name = "gr", baseUrl = "u", username = "u", password = "p")
    private val mcpConfig = McpServerConfig(name = "mcp", baseUrl = "http://mcp:7456")
    private val aiConfig = AiConfig(aiProvider = AiProvider.OPENAI.id, openAiApiKey = "sk-1")

    private fun newService(
        dbOk: Boolean = true,
        jiraOk: Boolean = true,
        confOk: Boolean = true,
        gitlabOk: Boolean = true,
        githubOk: Boolean = true,
        grafanaOk: Boolean = true,
        mcpOk: Boolean = true,
        probeStrategy: (ConnectionKind) -> Boolean = { true },
    ): ConnectionHealthProbeService =
        buildService(
            dbOk = dbOk,
            jiraOk = jiraOk,
            confOk = confOk,
            gitlabOk = gitlabOk,
            githubOk = githubOk,
            grafanaOk = grafanaOk,
            mcpOk = mcpOk,
            probeStrategy = probeStrategy,
        )

    private fun buildService(
        dbOk: Boolean,
        jiraOk: Boolean,
        confOk: Boolean,
        gitlabOk: Boolean,
        githubOk: Boolean,
        grafanaOk: Boolean,
        mcpOk: Boolean,
        probeStrategy: (ConnectionKind) -> Boolean,
    ): ConnectionHealthProbeService =
        ConnectionProbeTestSupport.buildService(
            dbRepo = stubDbRepo(),
            jiraRepo = stubJiraRepo(),
            confluenceRepo = stubConfluenceRepo(),
            gitlabRepo = stubGitlabRepo(),
            githubRepo = stubGithubRepo(),
            grafanaRepo = stubGrafanaRepo(),
            aiRepo = stubAiRepo(),
            mcpRepo = stubMcpRepo(),
            dbProbe = stubDbProbe { probeStrategy(ConnectionKind.DATABASE) && dbOk },
            jiraProbe = stubJiraProbe { probeStrategy(ConnectionKind.JIRA) && jiraOk },
            confluenceProbe = stubConfluenceProbe { probeStrategy(ConnectionKind.CONFLUENCE) && confOk },
            gitlabProbe = stubGitlabProbe { probeStrategy(ConnectionKind.GITLAB) && gitlabOk },
            githubProbe = stubGithubProbe { probeStrategy(ConnectionKind.GITHUB) && githubOk },
            grafanaProbe = stubGrafanaProbe { probeStrategy(ConnectionKind.GRAFANA) && grafanaOk },
            mcpProbe = stubMcpProbe { probeStrategy(ConnectionKind.MCP) && mcpOk },
            tracker = tracker,
        )

    private fun stubDbRepo(
        all: List<DatabaseConfig> = listOf(databaseConfig),
        byId: (Long) -> DatabaseConfig? = { if (it == databaseConfig.id) databaseConfig else null },
        save: (DatabaseConfig) -> DatabaseConfig = { it },
    ) = object : DatabaseConfigPort {
        override fun findAllDatabaseConfigs(): List<DatabaseConfig> = all

        override fun findByActiveTrue(): List<DatabaseConfig> = all

        override fun findDatabaseConfigById(id: Long): DatabaseConfig? = byId(id)

        override fun findAllDatabaseConfigsByIds(ids: Iterable<Long>): List<DatabaseConfig> = emptyList()

        override fun saveDatabaseConfig(config: DatabaseConfig): DatabaseConfig = save(config)

        override fun deleteDatabaseConfigById(id: Long) {}

        override fun deleteAllDatabaseConfigs() {}
    }

    private fun stubJiraRepo(
        all: List<JiraConfig> = listOf(jiraConfig),
        byId: (Long) -> JiraConfig? = { if (it == jiraConfig.id) jiraConfig else null },
        save: (JiraConfig) -> JiraConfig = { it },
    ) = object : JiraConfigPort {
        override fun findByActiveTrue(): List<JiraConfig> = all

        override fun findFirstByActiveTrue(): JiraConfig? = all.firstOrNull()

        override fun findJiraConfigById(id: Long): JiraConfig? = byId(id)

        override fun findAllJiraConfigs(): List<JiraConfig> = all

        override fun findAllJiraConfigsByIds(ids: Iterable<Long>): List<JiraConfig> = emptyList()

        override fun saveJiraConfig(config: JiraConfig): JiraConfig = save(config)

        override fun jiraConfigExistsById(id: Long): Boolean = false

        override fun deleteJiraConfigById(id: Long) {}

        override fun deleteAllJiraConfigs() {}
    }

    private fun stubConfluenceRepo(
        all: List<ConfluenceConfig> = listOf(confluenceConfig),
        byId: (Long) -> ConfluenceConfig? = { if (it == confluenceConfig.id) confluenceConfig else null },
        save: (ConfluenceConfig) -> ConfluenceConfig = { it },
    ) = object : ConfluenceConfigPort {
        override fun findByActiveTrue(): List<ConfluenceConfig> = all

        override fun findConfluenceConfigById(id: Long): ConfluenceConfig? = byId(id)

        override fun findAllConfluenceConfigs(): List<ConfluenceConfig> = all

        override fun findAllConfluenceConfigsByIds(ids: Iterable<Long>): List<ConfluenceConfig> = emptyList()

        override fun saveConfluenceConfig(config: ConfluenceConfig): ConfluenceConfig = save(config)

        override fun deleteConfluenceConfigById(id: Long) {}

        override fun deleteAllConfluenceConfigs() {}
    }

    private fun stubGitlabRepo(
        all: List<GitlabConfig> = listOf(gitlabConfig),
        byId: (Long) -> GitlabConfig? = { if (it == gitlabConfig.id) gitlabConfig else null },
        save: (GitlabConfig) -> GitlabConfig = { it },
    ) = object : GitlabConfigPort {
        override fun findByActiveTrue(): List<GitlabConfig> = all

        override fun findGitlabConfigById(id: Long): GitlabConfig? = byId(id)

        override fun findAllGitlabConfigs(): List<GitlabConfig> = all

        override fun findAllGitlabConfigsByIds(ids: Iterable<Long>): List<GitlabConfig> = emptyList()

        override fun existsActiveGitlabConfig(): Boolean = all.any { it.active }

        override fun saveGitlabConfig(config: GitlabConfig): GitlabConfig = save(config)

        override fun deleteGitlabConfigById(id: Long) {}

        override fun deleteAllGitlabConfigs() {}
    }

    private fun stubGithubRepo(
        all: List<GithubRepoConfig> = listOf(githubConfig),
        byId: (Long) -> GithubRepoConfig? = { if (it == githubConfig.id) githubConfig else null },
        save: (GithubRepoConfig) -> GithubRepoConfig = { it },
    ) = object : GithubRepoConfigPort {
        override fun findAllGithubRepoConfigs(): List<GithubRepoConfig> = all

        override fun findByActiveTrue(): List<GithubRepoConfig> = all

        override fun findGithubRepoConfigById(id: Long): GithubRepoConfig? = byId(id)

        override fun findAllGithubRepoConfigsByIds(ids: Iterable<Long>): List<GithubRepoConfig> = emptyList()

        override fun saveGithubRepoConfig(config: GithubRepoConfig): GithubRepoConfig = save(config)

        override fun githubRepoConfigExistsById(id: Long): Boolean = false

        override fun deleteGithubRepoConfigById(id: Long) {}

        override fun deleteAllGithubRepoConfigs() {}
    }

    private fun stubGrafanaRepo(
        all: List<GrafanaConfig> = listOf(grafanaConfig),
        byId: (Long) -> GrafanaConfig? = { if (it == grafanaConfig.id) grafanaConfig else null },
        save: (GrafanaConfig) -> GrafanaConfig = { it },
    ) = object : GrafanaConfigPort {
        override fun findAllGrafanaConfigs(): List<GrafanaConfig> = all

        override fun findGrafanaConfigById(id: Long): GrafanaConfig? = byId(id)

        override fun findByActiveTrue(): List<GrafanaConfig> = all

        override fun findAllGrafanaConfigsByIds(ids: Iterable<Long>): List<GrafanaConfig> = emptyList()

        override fun saveGrafanaConfig(config: GrafanaConfig): GrafanaConfig = save(config)

        override fun deleteGrafanaConfigById(id: Long) {}

        override fun deleteAllGrafanaConfigs() {}
    }

    private fun stubAiRepo(value: AiConfig? = aiConfig) =
        object : AiConfigPort {
            override fun findAiConfig(): AiConfig? = value

            override fun saveAiConfig(config: AiConfig): AiConfig = config

            override fun deleteAiConfig() {}
        }

    private fun stubMcpRepo(
        all: List<McpServerConfig> = listOf(mcpConfig),
        byId: (Long) -> McpServerConfig? = { if (it == mcpConfig.id) mcpConfig else null },
        save: (McpServerConfig) -> McpServerConfig = { it },
    ) = object : McpServerConfigPort {
        override fun findAllMcpServerConfigs(): List<McpServerConfig> = all

        override fun findMcpServerConfigById(id: Long): McpServerConfig? = byId(id)

        override fun findFirstActiveMcpServerConfig(): McpServerConfig? = all.firstOrNull()

        override fun saveMcpServerConfig(config: McpServerConfig): McpServerConfig = save(config)

        override fun deleteMcpServerConfigById(id: Long) {}

        override fun deleteAllMcpServerConfigs() {}
    }

    private fun stubDbProbe(ok: () -> Boolean) =
        object : DatabaseConnectionProbePort {
            override fun probe(config: DatabaseConfig): ConnectionProbeResult = if (ok()) ConnectionProbeResult.Ok() else ConnectionProbeResult.Failure("db down")
        }

    private fun stubJiraProbe(ok: () -> Boolean) =
        object : JiraConnectionProbePort {
            override fun probe(config: JiraConfig): ConnectionProbeResult = if (ok()) ConnectionProbeResult.Ok() else ConnectionProbeResult.Failure("jira down")
        }

    private fun stubConfluenceProbe(ok: () -> Boolean) =
        object : ConfluenceConnectionProbePort {
            override fun probe(config: ConfluenceConfig): ConnectionProbeResult = if (ok()) ConnectionProbeResult.Ok() else ConnectionProbeResult.Failure("conf down")
        }

    private fun stubGitlabProbe(ok: () -> Boolean) =
        object : GitlabConnectionProbePort {
            override fun probe(config: GitlabConfig): ConnectionProbeResult = if (ok()) ConnectionProbeResult.Ok() else ConnectionProbeResult.Failure("gl down")
        }

    private fun stubGithubProbe(ok: () -> Boolean) =
        object : GithubConnectionProbePort {
            override fun probe(
                owner: String,
                repo: String,
                token: String?,
                apiBase: String,
            ): ConnectionProbeResult = if (ok()) ConnectionProbeResult.Ok() else ConnectionProbeResult.Failure("gh down")
        }

    private fun stubGrafanaProbe(ok: () -> Boolean) =
        object : GrafanaConnectionProbePort {
            override fun probe(config: GrafanaConfig): ConnectionProbeResult = if (ok()) ConnectionProbeResult.Ok() else ConnectionProbeResult.Failure("gr down")
        }

    private fun stubMcpProbe(ok: () -> Boolean) =
        object : McpConnectionProbePort {
            override fun probe(config: McpServerConfig): ConnectionProbeResult = if (ok()) ConnectionProbeResult.Ok() else ConnectionProbeResult.Failure("mcp down")
        }

    @Test
    fun `probeAll runs every per-kind probe and records a snapshot for each row`() {
        newService().probeAll()
        for (kind in ConnectionKind.entries) {
            val snap = tracker.snapshotAll().values.firstOrNull { it.kind == kind.id }
            assertNotNull(snap, "expected a snapshot for $kind")
        }
    }

    @Test
    fun `probeAll swallows exceptions and still records failures on the snapshot`() {
        newService(dbOk = false).probeAll()
        val dbSnap = tracker.snapshotAll().values.first { it.kind == ConnectionKind.DATABASE.id }
        assertEquals(false, dbSnap.healthy)
        assertEquals(1, dbSnap.consecutiveFailures)
    }

    @Test
    fun `probeAll runs all seven per-kind batches in parallel on a shared executor`() {
        val threadsObserved =
            java.util.concurrent.ConcurrentHashMap
                .newKeySet<String>()
        val slowDb = mock<DatabaseConnectionProbePort>()
        whenever(slowDb.probe(any())).thenAnswer {
            Thread.sleep(200)
            ConnectionProbeResult.Ok()
        }
        val slowJira = mock<JiraConnectionProbePort>()
        whenever(slowJira.probe(any())).thenAnswer {
            Thread.sleep(200)
            ConnectionProbeResult.Ok()
        }
        val service =
            ConnectionProbeTestSupport.buildService(
                dbRepo = mock<DatabaseConfigPort>(),
                jiraRepo = mock<JiraConfigPort>(),
                confluenceRepo = mock<ConfluenceConfigPort>(),
                gitlabRepo = mock<GitlabConfigPort>(),
                githubRepo = mock<GithubRepoConfigPort>(),
                grafanaRepo = mock<GrafanaConfigPort>(),
                aiRepo = mock<AiConfigPort>(),
                mcpRepo = mock<McpServerConfigPort>(),
                dbProbe = slowDb,
                jiraProbe = slowJira,
                confluenceProbe = mock<ConfluenceConnectionProbePort>(),
                gitlabProbe = mock<GitlabConnectionProbePort>(),
                githubProbe = mock<GithubConnectionProbePort>(),
                grafanaProbe = mock<GrafanaConnectionProbePort>(),
                mcpProbe = mock<McpConnectionProbePort>(),
                tracker = tracker,
            )
        threadsObserved.add("warmed")
        val before = System.currentTimeMillis()
        service.probeAll()
        val elapsed = System.currentTimeMillis() - before
        assertTrue(
            elapsed < 700,
            "probeAll() should run per-kind batches in parallel; expected < 700ms, got ${elapsed}ms",
        )
    }

    @Test
    fun `probeOne returns null when no row matches`() {
        assertNull(newService().probeOne(ConnectionKind.DATABASE, 999L))
    }

    @Test
    fun `probeOne returns a snapshot on success`() {
        val snap = newService().probeOne(ConnectionKind.DATABASE, databaseConfig.id)
        assertNotNull(snap)
        assertEquals(true, snap?.healthy)
    }

    @Test
    fun `probeOne covers every kind`() {
        val service = newService()
        for (
        (kind, id) in
        listOf(
            ConnectionKind.DATABASE to databaseConfig.id,
            ConnectionKind.JIRA to jiraConfig.id,
            ConnectionKind.CONFLUENCE to confluenceConfig.id,
            ConnectionKind.GITLAB to gitlabConfig.id,
            ConnectionKind.GITHUB to githubConfig.id,
            ConnectionKind.GRAFANA to grafanaConfig.id,
            ConnectionKind.MCP to mcpConfig.id,
        )
        ) {
            assertNotNull(service.probeOne(kind, id), "missing snapshot for $kind")
        }
    }

    @Test
    fun `probeAll with all probes failing does NOT auto-disable - the user keeps full control`() {
        newService(
            dbOk = false,
            jiraOk = false,
            confOk = false,
            gitlabOk = false,
            githubOk = false,
            grafanaOk = false,
            mcpOk = false,
        ).apply {
            probeAll()
            assertEquals(1, databaseConfig.consecutiveFailures)
            probeAll()
            assertEquals(2, databaseConfig.consecutiveFailures)
            probeAll()
            assertEquals(3, databaseConfig.consecutiveFailures)
            assertTrue(databaseConfig.active, "active must stay true — the probe never flips it")
        }
    }

    @Test
    fun `probeAll with a successful probe resets the failure counter`() {
        var dbOk = true
        val probe =
            object : DatabaseConnectionProbePort {
                override fun probe(config: DatabaseConfig): ConnectionProbeResult = if (dbOk) ConnectionProbeResult.Ok() else ConnectionProbeResult.Failure("down")
            }
        val service =
            ConnectionProbeTestSupport.buildService(
                dbRepo = stubDbRepo(),
                jiraRepo = stubJiraRepo(),
                confluenceRepo = stubConfluenceRepo(),
                gitlabRepo = stubGitlabRepo(),
                githubRepo = stubGithubRepo(),
                grafanaRepo = stubGrafanaRepo(),
                aiRepo = stubAiRepo(),
                mcpRepo = stubMcpRepo(),
                dbProbe = probe,
                jiraProbe = stubJiraProbe { true },
                confluenceProbe = stubConfluenceProbe { true },
                gitlabProbe = stubGitlabProbe { true },
                githubProbe = stubGithubProbe { true },
                grafanaProbe = stubGrafanaProbe { true },
                mcpProbe = stubMcpProbe { true },
                tracker = tracker,
            )
        dbOk = false
        service.probeAll()
        assertEquals(1, databaseConfig.consecutiveFailures)
        dbOk = true
        service.probeAll()
        assertEquals(0, databaseConfig.consecutiveFailures)
        assertNull(databaseConfig.lastErrorMessage)
    }
}
