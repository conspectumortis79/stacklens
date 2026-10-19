package com.stacklens.application.service.health
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.ConfluenceConfigPort
import com.stacklens.application.port.out.DatabaseConfigPort
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.GrafanaConfigPort
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.health.ConfluenceConnectionProbePort
import com.stacklens.application.port.out.health.DatabaseConnectionProbePort
import com.stacklens.application.port.out.health.GithubConnectionProbePort
import com.stacklens.application.port.out.health.GitlabConnectionProbePort
import com.stacklens.application.port.out.health.GrafanaConnectionProbePort
import com.stacklens.application.port.out.health.JiraConnectionProbePort
import com.stacklens.application.port.out.health.McpConnectionProbePort
import com.stacklens.application.port.out.health.MutableConnectionHealthPort
import com.stacklens.testsupport.ImmediateStartupTaskExecutor

internal object ConnectionProbeTestSupport {
    fun buildService(
        dbRepo: DatabaseConfigPort,
        jiraRepo: JiraConfigPort,
        confluenceRepo: ConfluenceConfigPort,
        gitlabRepo: GitlabConfigPort,
        githubRepo: GithubRepoConfigPort,
        grafanaRepo: GrafanaConfigPort,
        aiRepo: AiConfigPort,
        mcpRepo: McpServerConfigPort,
        dbProbe: DatabaseConnectionProbePort,
        jiraProbe: JiraConnectionProbePort,
        confluenceProbe: ConfluenceConnectionProbePort,
        gitlabProbe: GitlabConnectionProbePort,
        githubProbe: GithubConnectionProbePort,
        grafanaProbe: GrafanaConnectionProbePort,
        mcpProbe: McpConnectionProbePort,
        tracker: MutableConnectionHealthPort,
    ): ConnectionHealthProbeService {
        val adapters =
            listOf(
                DatabaseConnectionProbeAdapter(repo = dbRepo, probe = dbProbe, tracker = tracker, clock = com.stacklens.adapter.out.time.SystemClockPort),
                JiraConnectionProbeAdapter(repo = jiraRepo, probe = jiraProbe, tracker = tracker, clock = com.stacklens.adapter.out.time.SystemClockPort),
                ConfluenceConnectionProbeAdapter(repo = confluenceRepo, probe = confluenceProbe, tracker = tracker, clock = com.stacklens.adapter.out.time.SystemClockPort),
                GitlabConnectionProbeAdapter(repo = gitlabRepo, probe = gitlabProbe, tracker = tracker, clock = com.stacklens.adapter.out.time.SystemClockPort),
                GithubConnectionProbeAdapter(repo = githubRepo, probe = githubProbe, aiRepo = aiRepo, tracker = tracker, clock = com.stacklens.adapter.out.time.SystemClockPort),
                GrafanaConnectionProbeAdapter(repo = grafanaRepo, probe = grafanaProbe, tracker = tracker, clock = com.stacklens.adapter.out.time.SystemClockPort),
                McpConnectionProbeAdapter(repo = mcpRepo, probe = mcpProbe, tracker = tracker, clock = com.stacklens.adapter.out.time.SystemClockPort),
            )
        @Suppress("ktlint:standard:multiline-expression-wrapping", "ktlint:standard:chain-method-continuation")
        return ConnectionHealthProbeService(
            adapters = adapters,
            probeExecutor = java.util.concurrent.Executors.newSingleThreadExecutor(),
            startupTasks = ImmediateStartupTaskExecutor,
        )
    }
}
