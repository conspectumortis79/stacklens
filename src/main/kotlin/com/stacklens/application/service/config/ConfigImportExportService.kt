package com.stacklens.application.service.config

import com.stacklens.application.port.inbound.config.ConfigImportRequest
import com.stacklens.application.port.inbound.config.ConfigImportUseCase
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.ConfluenceConfigPort
import com.stacklens.application.port.out.DatabaseConfigPort
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.GrafanaConfigPort
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.UiPreferencesPort
import com.stacklens.application.port.out.config.DemoUrlAlignmentPort
import com.stacklens.application.port.out.textfilter.TextFilterConfigPort
import com.stacklens.application.port.out.transaction.TransactionPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.AiConfig
import com.stacklens.domain.model.mergeImportedSettings
import org.slf4j.LoggerFactory

@ApplicationService
class ConfigImportExportService(
    private val gitlabConfigRepo: GitlabConfigPort,
    private val dbConfigRepo: DatabaseConfigPort,
    private val confluenceConfigRepo: ConfluenceConfigPort,
    private val grafanaConfigRepo: GrafanaConfigPort,
    private val jiraConfigRepo: JiraConfigPort,
    private val aiConfigRepo: AiConfigPort,
    private val githubRepoConfigRepo: GithubRepoConfigPort,
    private val mcpServerConfigRepo: McpServerConfigPort,
    private val tx: TransactionPort,
    private val uiPreferences: UiPreferencesPort,
    private val textFilterConfig: TextFilterConfigPort,
    private val demoUrlAlignment: DemoUrlAlignmentPort,
) : ConfigImportUseCase {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun hasAnyConfig(): Boolean =
        gitlabConfigRepo.findAllGitlabConfigs().isNotEmpty() ||
            dbConfigRepo.findAllDatabaseConfigs().isNotEmpty() ||
            confluenceConfigRepo.findAllConfluenceConfigs().isNotEmpty() ||
            grafanaConfigRepo.findAllGrafanaConfigs().isNotEmpty() ||
            jiraConfigRepo.findAllJiraConfigs().isNotEmpty() ||
            aiConfigRepo.findAiConfig() != null ||
            githubRepoConfigRepo.findAllGithubRepoConfigs().isNotEmpty() ||
            mcpServerConfigRepo.findAllMcpServerConfigs().isNotEmpty()

    override fun importConfigs(
        request: ConfigImportRequest,
        skipIfNotEmpty: Boolean,
    ): Map<String, Int> {
        val stats = mutableMapOf<String, Int>()

        tx.inTransaction {
            importCollection(
                items = request.gitlabConfigs,
                skipIfNotEmpty = skipIfNotEmpty,
                isEmpty = { gitlabConfigRepo.findAllGitlabConfigs().isEmpty() },
                deleteAll = { gitlabConfigRepo.deleteAllGitlabConfigs() },
                saveOne = { gitlabConfigRepo.saveGitlabConfig(it) },
                statsKey = "gitlab",
                stats = stats,
            )
            importCollection(
                items = request.databaseConfigs,
                skipIfNotEmpty = skipIfNotEmpty,
                isEmpty = { dbConfigRepo.findAllDatabaseConfigs().isEmpty() },
                deleteAll = { dbConfigRepo.deleteAllDatabaseConfigs() },
                saveOne = { dbConfigRepo.saveDatabaseConfig(it) },
                statsKey = "databases",
                stats = stats,
            )
            importCollection(
                items = request.confluenceConfigs,
                skipIfNotEmpty = skipIfNotEmpty,
                isEmpty = { confluenceConfigRepo.findAllConfluenceConfigs().isEmpty() },
                deleteAll = { confluenceConfigRepo.deleteAllConfluenceConfigs() },
                saveOne = { confluenceConfigRepo.saveConfluenceConfig(it) },
                statsKey = "confluence",
                stats = stats,
            )
            importCollection(
                items = request.grafanaConfigs,
                skipIfNotEmpty = skipIfNotEmpty,
                isEmpty = { grafanaConfigRepo.findAllGrafanaConfigs().isEmpty() },
                deleteAll = { grafanaConfigRepo.deleteAllGrafanaConfigs() },
                saveOne = { grafanaConfigRepo.saveGrafanaConfig(it) },
                statsKey = "grafana",
                stats = stats,
            )
            importCollection(
                items = request.jiraConfigs,
                skipIfNotEmpty = skipIfNotEmpty,
                isEmpty = { jiraConfigRepo.findAllJiraConfigs().isEmpty() },
                deleteAll = { jiraConfigRepo.deleteAllJiraConfigs() },
                saveOne = { jiraConfigRepo.saveJiraConfig(it) },
                statsKey = "jira",
                stats = stats,
            )
            importAiSettings(request, skipIfNotEmpty, stats)
            importCollection(
                items = request.githubRepoConfigs,
                skipIfNotEmpty = skipIfNotEmpty,
                isEmpty = { githubRepoConfigRepo.findAllGithubRepoConfigs().isEmpty() },
                deleteAll = { githubRepoConfigRepo.deleteAllGithubRepoConfigs() },
                saveOne = { githubRepoConfigRepo.saveGithubRepoConfig(it) },
                statsKey = "github",
                stats = stats,
            )
            importCollection(
                items = request.mcpServerConfigs,
                skipIfNotEmpty = skipIfNotEmpty,
                isEmpty = { mcpServerConfigRepo.findAllMcpServerConfigs().isEmpty() },
                deleteAll = { mcpServerConfigRepo.deleteAllMcpServerConfigs() },
                saveOne = { mcpServerConfigRepo.saveMcpServerConfig(it) },
                statsKey = "mcp",
                stats = stats,
            )
            importUiSettings(request, skipIfNotEmpty, stats)
        }

        alignImportedDemoUrls()
        return stats
    }

    private fun alignImportedDemoUrls() {
        val aligned = demoUrlAlignment.alignAll()
        if (aligned.isNotEmpty()) {
            log.info(
                "[ConfigImport] rewrote {} imported demo connection URL(s) to match the current runtime: {}",
                aligned.size,
                aligned.joinToString(),
            )
        }
    }

    private fun importUiSettings(
        request: ConfigImportRequest,
        skipIfNotEmpty: Boolean,
        stats: MutableMap<String, Int>,
    ) {
        val developer = request.developerOptions
        val hasSettings = request.uiPreferences.isNotEmpty() || request.theme != null || developer != null
        if (!hasSettings) return
        if (skipIfNotEmpty && uiPreferences.getAll().isNotEmpty()) return
        val imported = request.uiPreferences.toMutableMap()
        request.theme?.let { imported[UiPreferenceVocabulary.THEME_KEY] = UiPreferenceVocabulary.normalizeTheme(it) }
        developer?.let { imported.putAll(UiPreferenceVocabulary.toPreferenceEntries(it)) }
        uiPreferences.replaceAll(imported)
        developer?.let { textFilterConfig.setEnabled(it.textFilterEnabled) }
        stats["preferences"] = 1
    }

    private fun <T> importCollection(
        items: List<T>,
        skipIfNotEmpty: Boolean,
        isEmpty: () -> Boolean,
        deleteAll: () -> Unit,
        saveOne: (T) -> Any,
        statsKey: String,
        stats: MutableMap<String, Int>,
    ) {
        if (items.isEmpty()) return
        if (skipIfNotEmpty && !isEmpty()) return
        deleteAll()
        items.forEach { saveOne(it) }
        stats[statsKey] = items.size
    }

    private fun importAiSettings(
        request: ConfigImportRequest,
        skipIfNotEmpty: Boolean,
        stats: MutableMap<String, Int>,
    ) {
        val importedAi = request.aiConfig
        val importedLanguage = request.language?.takeIf { it == "en" || it == "de" }
        val hasAiSettings = importedAi != null || importedLanguage != null || request.availableModels != null
        if (!hasAiSettings) return
        val existing = aiConfigRepo.findAiConfig()
        if (skipIfNotEmpty && existing != null) return
        val target = existing ?: AiConfig(id = 1L)
        importedAi?.let { ai -> target.mergeImportedSettings(ai, request.replaceAiConfig) }
        importedLanguage?.let { target.language = it }
        request.availableModels?.let { target.availableModels = it }
        aiConfigRepo.saveAiConfig(target)
        stats["ai"] = 1
    }
}
