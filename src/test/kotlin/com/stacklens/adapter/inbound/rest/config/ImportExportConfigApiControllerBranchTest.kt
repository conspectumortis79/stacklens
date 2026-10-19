package com.stacklens.adapter.inbound.rest.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.inbound.rest.ImportFromPathRequest
import com.stacklens.adapter.inbound.rest.SaveConfigToPathRequest
import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.adapter.out.security.ConfigEnvelope
import com.stacklens.adapter.out.security.ConfigEnvelopeCipherAdapter
import com.stacklens.application.port.inbound.config.ConfigImportUseCase
import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.application.port.inbound.workspace.TextFileSaveRequest
import com.stacklens.application.port.inbound.workspace.WorkspaceFileUseCase
import com.stacklens.application.port.inbound.workspace.WorkspaceWriteRejection
import com.stacklens.application.port.inbound.workspace.WorkspaceWriteResult
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.config.AiConfigReadPort
import com.stacklens.application.port.out.config.ConfluenceConfigReadPort
import com.stacklens.application.port.out.config.DatabaseConfigReadPort
import com.stacklens.application.port.out.config.GitlabConfigReadPort
import com.stacklens.application.port.out.config.GrafanaConfigReadPort
import com.stacklens.application.port.out.config.JiraConfigReadPort
import com.stacklens.application.service.config.ConfigEnvelopeService
import com.stacklens.application.service.config.ConfigExportService
import com.stacklens.application.service.workspace.WorkspaceFileService
import com.stacklens.testsupport.FixedClockPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import org.springframework.mock.web.MockMultipartFile

private const val EXPORT_EPOCH = 1_750_000_000_000L

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImportExportConfigApiControllerBranchTest {
    @Mock private lateinit var gitlabConfigs: GitlabConfigReadPort

    @Mock private lateinit var databaseConfigs: DatabaseConfigReadPort

    @Mock private lateinit var confluenceConfigs: ConfluenceConfigReadPort

    @Mock private lateinit var grafanaConfigs: GrafanaConfigReadPort

    @Mock private lateinit var jiraConfigs: JiraConfigReadPort

    @Mock private lateinit var aiConfig: AiConfigReadPort

    @Mock private lateinit var configImport: ConfigImportUseCase

    @Mock private lateinit var i18n: I18nUseCase

    @Mock private lateinit var githubRepoConfigPort: GithubRepoConfigPort

    @Mock private lateinit var mcpServerConfigPort: McpServerConfigPort

    private val workspaceFiles: WorkspaceFileUseCase =
        spy(WorkspaceFileService(JavaFileSystemAdapter(), JavaFileSystemAdapter(), JavaFileSystemAdapter(), JavaFileSystemAdapter()))

    private val objectMapper = ObjectMapper()

    private val controller by lazy {
        ImportExportConfigApiController(
            configExport =
                ConfigExportService(
                    gitlabConfigs,
                    databaseConfigs,
                    confluenceConfigs,
                    grafanaConfigs,
                    jiraConfigs,
                    aiConfig,
                    githubRepoConfigPort,
                    mcpServerConfigPort,
                    StubUiPreferences(),
                    StubTextFilter(),
                    FixedClockPort(EXPORT_EPOCH),
                ),
            configImport = configImport,
            workspaceFiles = workspaceFiles,
            i18n = i18n,
            exportDtoMapper = ConfigExportDtoMapper(),
            importMapper = ConfigImportMapper(),
            envelopeCodec = ConfigEnvelopeCodec(objectMapper, ConfigEnvelopeService(ConfigEnvelopeCipherAdapter(objectMapper))),
        )
    }

    @BeforeEach
    fun setUp() {
        whenever(configImport.hasAnyConfig()).thenReturn(false)
    }

    @Test
    fun `importConfig catches exception and returns 500`() {
        val plainConfig = """{"version": 1, "gitlab": [], "databases": [], "confluence": [], "grafana": [], "jira": []}"""
        val file = MockMultipartFile("file", "config.json", "application/json", plainConfig.toByteArray())
        whenever(configImport.importConfigs(any(), any())).thenThrow(RuntimeException("DB error"))
        whenever(i18n.t(any<String>(), any<Any>(), any<Any>())).thenReturn("Import failed: DB error")
        whenever(i18n.t(any<String>(), any<Any>(), any<Any>(), any<Any>())).thenReturn("Import failed: DB error")
        whenever(i18n.t("config.be_unknown_error")).thenReturn("unknown error")
        val result = controller.importConfig(file, null)
        assertEquals(500, result.statusCode.value())
    }

    @Test
    fun `importConfig handles encrypted config with right password`() {
        val plainConfig = """{"version":1,"exportedAt":"2024-01-01T00:00:00","gitlab":[],"databases":[],"confluence":[],"grafana":[],"jira":[]}"""
        val password = "testpassword"
        val envelope =
            ConfigEnvelopeCipherAdapter(objectMapper).encrypt(plainConfig.toByteArray(), password)
        val file = MockMultipartFile("file", "config.json", "application/json", envelope)
        val result = controller.importConfig(file, password)
        assertEquals(200, result.statusCode.value())
        assertEquals(true, result.body!!["success"])
    }

    @Test
    fun `importConfig handles encrypted config with non-AEAD decrypt error`() {
        val envelope =
            ConfigEnvelope(
                iterations = 100,
                salt = "invalid-base64",
                iv = "invalid-base64",
                data = "invalid-base64",
            )
        val envelopeJson = objectMapper.writeValueAsString(envelope)
        val file = MockMultipartFile("file", "config.json", "application/json", envelopeJson.toByteArray())
        whenever(i18n.t(any<String>(), any<Any>())).thenReturn("Decrypt error")
        val result = controller.importConfig(file, "anypassword")
        assertEquals(400, result.statusCode.value())
    }

    @Test
    fun `importConfig handles JSON parse error`() {
        val invalidJson = "not valid json at all"
        val file = MockMultipartFile("file", "config.json", "application/json", invalidJson.toByteArray())
        whenever(i18n.t(any<String>(), any<Any>())).thenReturn("Invalid JSON")
        val result = controller.importConfig(file, null)
        assertEquals(400, result.statusCode.value())
    }

    @Test
    fun `importConfigFromPath returns 500 on import error`() {
        val content = """{"version":1,"exportedAt":"2024-01-01T00:00:00","gitlab":[],"databases":[],"confluence":[],"grafana":[],"jira":[]}"""
        val configFile = java.io.File.createTempFile("config", ".json")
        try {
            configFile.writeText(content)
            whenever(configImport.importConfigs(any(), any())).thenThrow(RuntimeException("DB error"))
            whenever(i18n.t(any<String>(), any<Any>(), any<Any>())).thenReturn("Import failed: DB error")
            whenever(i18n.t(any<String>(), any<Any>(), any<Any>(), any<Any>())).thenReturn("Import failed: DB error")
            whenever(i18n.t("config.be_unknown_error")).thenReturn("unknown error")
            val req = ImportFromPathRequest(path = configFile.absolutePath)
            val result = controller.importConfigFromPath(req)
            assertEquals(500, result.statusCode.value())
        } finally {
            configFile.delete()
        }
    }

    @Test
    fun `importConfigFromPath with skipIfNotEmpty true calls importConfigs with that flag`() {
        val content = """{"version":1,"exportedAt":"2024-01-01T00:00:00","gitlab":[],"databases":[],"confluence":[],"grafana":[],"jira":[]}"""
        val configFile = java.io.File.createTempFile("config", ".json")
        try {
            configFile.writeText(content)
            val req = ImportFromPathRequest(path = configFile.absolutePath, skipIfNotEmpty = true)
            val result = controller.importConfigFromPath(req)
            assertEquals(200, result.statusCode.value())
        } finally {
            configFile.delete()
        }
    }

    @Test
    fun `saveConfigToPath returns 400 when dir does not exist`() {
        val req = SaveConfigToPathRequest(path = "/nonexistent/dir", filename = "x.txt", content = "x")
        org.mockito.Mockito
            .doReturn(WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.DIRECTORY_NOT_FOUND))
            .`when`(workspaceFiles)
            .saveText(any())
        whenever(i18n.t("config.be_dir_not_found")).thenReturn("Dir not found")
        val result = controller.saveConfigToPath(req)
        assertEquals(400, result.statusCode.value())
        assertEquals("Dir not found", result.body!!["error"])
    }

    @Test
    fun `saveConfigToPath returns 500 when the write fails`() {
        val req = SaveConfigToPathRequest(path = "/tmp", filename = "x.txt", content = "x")
        org.mockito.Mockito
            .doReturn(WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.WRITE_FAILED))
            .`when`(workspaceFiles)
            .saveText(any())
        whenever(i18n.t("config.be_save_error", "")).thenReturn("Save failed")
        val result = controller.saveConfigToPath(req)
        assertEquals(500, result.statusCode.value())
        assertEquals("Save failed", result.body!!["error"])
    }

    @Test
    fun `saveConfigToPath forwards the unsanitized filename to the use case`() {
        val req = SaveConfigToPathRequest(path = "/tmp", filename = "../etc/passwd", content = "x")
        org.mockito.Mockito
            .doReturn(WorkspaceWriteResult.Saved("/tmp/passwd"))
            .`when`(workspaceFiles)
            .saveText(any())
        val result = controller.saveConfigToPath(req)
        assertEquals(200, result.statusCode.value())
        assertEquals("/tmp/passwd", result.body!!["savedPath"])
        verify(workspaceFiles).saveText(
            TextFileSaveRequest(directory = "/tmp", filename = "../etc/passwd", content = "x"),
        )
    }

    @Test
    fun `hasAnyConfig returns configured false when nothing configured`() {
        val result = controller.hasAnyConfig()
        assertEquals(false, result["configured"])
    }
}

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImportExportConfigApiControllerMcpImportTest {
    @Mock private lateinit var gitlabConfigs: GitlabConfigReadPort

    @Mock private lateinit var databaseConfigs: DatabaseConfigReadPort

    @Mock private lateinit var confluenceConfigs: ConfluenceConfigReadPort

    @Mock private lateinit var grafanaConfigs: GrafanaConfigReadPort

    @Mock private lateinit var jiraConfigs: JiraConfigReadPort

    @Mock private lateinit var aiConfig: AiConfigReadPort

    @Mock private lateinit var configImport: ConfigImportUseCase

    @Mock private lateinit var i18n: I18nUseCase

    @Mock private lateinit var githubRepoConfigPort: GithubRepoConfigPort

    @Mock private lateinit var mcpServerConfigPort: McpServerConfigPort

    private val workspaceFiles: WorkspaceFileUseCase =
        spy(WorkspaceFileService(JavaFileSystemAdapter(), JavaFileSystemAdapter(), JavaFileSystemAdapter(), JavaFileSystemAdapter()))

    private val objectMapper = ObjectMapper()

    private val controller by lazy {
        ImportExportConfigApiController(
            configExport =
                ConfigExportService(
                    gitlabConfigs,
                    databaseConfigs,
                    confluenceConfigs,
                    grafanaConfigs,
                    jiraConfigs,
                    aiConfig,
                    githubRepoConfigPort,
                    mcpServerConfigPort,
                    StubUiPreferences(),
                    StubTextFilter(),
                    FixedClockPort(EXPORT_EPOCH),
                ),
            configImport = configImport,
            workspaceFiles = workspaceFiles,
            i18n = i18n,
            exportDtoMapper = ConfigExportDtoMapper(),
            importMapper = ConfigImportMapper(),
            envelopeCodec = ConfigEnvelopeCodec(objectMapper, ConfigEnvelopeService(ConfigEnvelopeCipherAdapter(objectMapper))),
        )
    }

    @BeforeEach
    fun setUp() {
        whenever(configImport.importConfigs(any(), any())).thenReturn(
            emptyMap(),
        )
    }

    @Test
    fun `importConfigFromBytes with mcp config containing non-blank auth tokens sets every field`() {
        val json =
            """
            {
              "version": 1,
              "exportedAt": "2024-01-01T00:00:00",
              "gitlab": [],
              "databases": [],
              "confluence": [],
              "grafana": [],
              "jira": [],
              "github": [],
              "mcp": [{
                "name": "my-mcp",
                "baseUrl": "http://localhost:7456",
                "active": true,
                "authToken": "secret",
                "routeJira": true,
                "routeConfluence": true,
                "routeGitlab": true,
                "routeGithub": true,
                "routeGrafana": true,
                "routeDatabase": true,
                "jiraBaseUrl": "https://jira",
                "jiraUsername": "u",
                "jiraApiToken": "t",
                "jiraCloudMode": true,
                "confluenceBaseUrl": "https://conf",
                "confluenceUsername": "u",
                "confluenceApiToken": "t",
                "confluenceCloudMode": true,
                "gitlabBaseUrl": "https://gl",
                "gitlabAccessToken": "t",
                "githubApiBase": "https://api.github.com",
                "githubToken": "ghp_xyz",
                "grafanaBaseUrl": "https://graf",
                "grafanaApiKey": "key",
                "autoStartManaged": true
              }]
            }
            """.trimIndent()
        val file =
            MockMultipartFile(
                "file",
                "config.json",
                "application/json",
                json.toByteArray(),
            )
        val result = controller.importConfig(file, null)
        assertEquals(200, result.statusCode.value())
    }
}

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImportExportConfigApiControllerNullMessageTest {
    @Mock private lateinit var gitlabConfigs: GitlabConfigReadPort

    @Mock private lateinit var databaseConfigs: DatabaseConfigReadPort

    @Mock private lateinit var confluenceConfigs: ConfluenceConfigReadPort

    @Mock private lateinit var grafanaConfigs: GrafanaConfigReadPort

    @Mock private lateinit var jiraConfigs: JiraConfigReadPort

    @Mock private lateinit var aiConfig: AiConfigReadPort

    @Mock private lateinit var configImport: ConfigImportUseCase

    @Mock private lateinit var i18n: I18nUseCase

    @Mock private lateinit var githubRepoConfigPort: GithubRepoConfigPort

    @Mock private lateinit var mcpServerConfigPort: McpServerConfigPort

    private val workspaceFiles: WorkspaceFileUseCase =
        spy(WorkspaceFileService(JavaFileSystemAdapter(), JavaFileSystemAdapter(), JavaFileSystemAdapter(), JavaFileSystemAdapter()))

    private val objectMapper = ObjectMapper()

    private val controller by lazy {
        ImportExportConfigApiController(
            configExport =
                ConfigExportService(
                    gitlabConfigs,
                    databaseConfigs,
                    confluenceConfigs,
                    grafanaConfigs,
                    jiraConfigs,
                    aiConfig,
                    githubRepoConfigPort,
                    mcpServerConfigPort,
                    StubUiPreferences(),
                    StubTextFilter(),
                    FixedClockPort(EXPORT_EPOCH),
                ),
            configImport = configImport,
            workspaceFiles = workspaceFiles,
            i18n = i18n,
            exportDtoMapper = ConfigExportDtoMapper(),
            importMapper = ConfigImportMapper(),
            envelopeCodec = ConfigEnvelopeCodec(objectMapper, ConfigEnvelopeService(ConfigEnvelopeCipherAdapter(objectMapper))),
        )
    }

    @Test
    fun `importConfig returns 500 with unknown error message when exception has null message`() {
        val plainConfig = """{"version": 1, "gitlab": [], "databases": [], "confluence": [], "grafana": [], "jira": []}"""
        val file = MockMultipartFile("file", "config.json", "application/json", plainConfig.toByteArray())
        whenever(configImport.importConfigs(any(), any())).thenAnswer { throw RuntimeException() }
        whenever(i18n.t("config.be_unknown_error")).thenReturn("unknown error")
        whenever(i18n.t(eq("config.be_import_failed"), any<Any>(), any<Any>())).thenReturn("Import failed: <unknown>")
        val result = controller.importConfig(file, null)
        assertEquals(500, result.statusCode.value())
    }

    @Test
    fun `importConfigFromPath returns 500 with unknown error message when exception has null message`() {
        val content = """{"version":1,"exportedAt":"2024-01-01T00:00:00","gitlab":[],"databases":[],"confluence":[],"grafana":[],"jira":[]}"""
        val configFile = java.io.File.createTempFile("config", ".json")
        try {
            configFile.writeText(content)
            whenever(configImport.importConfigs(any(), any())).thenAnswer { throw RuntimeException() }
            whenever(i18n.t("config.be_unknown_error")).thenReturn("unknown error")
            whenever(i18n.t(eq("config.be_import_failed"), any<Any>(), any<Any>())).thenReturn("Import failed: <unknown>")
            val req = ImportFromPathRequest(path = configFile.absolutePath)
            val result = controller.importConfigFromPath(req)
            assertEquals(500, result.statusCode.value())
        } finally {
            configFile.delete()
        }
    }
}

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImportExportConfigApiControllerPathIsDirTest {
    @Mock private lateinit var gitlabConfigs: GitlabConfigReadPort

    @Mock private lateinit var databaseConfigs: DatabaseConfigReadPort

    @Mock private lateinit var confluenceConfigs: ConfluenceConfigReadPort

    @Mock private lateinit var grafanaConfigs: GrafanaConfigReadPort

    @Mock private lateinit var jiraConfigs: JiraConfigReadPort

    @Mock private lateinit var aiConfig: AiConfigReadPort

    @Mock private lateinit var configImport: ConfigImportUseCase

    @Mock private lateinit var i18n: I18nUseCase

    @Mock private lateinit var githubRepoConfigPort: GithubRepoConfigPort

    @Mock private lateinit var mcpServerConfigPort: McpServerConfigPort

    private val workspaceFiles: WorkspaceFileUseCase =
        spy(WorkspaceFileService(JavaFileSystemAdapter(), JavaFileSystemAdapter(), JavaFileSystemAdapter(), JavaFileSystemAdapter()))

    private val objectMapper = ObjectMapper()

    private val controller by lazy {
        ImportExportConfigApiController(
            configExport =
                ConfigExportService(
                    gitlabConfigs,
                    databaseConfigs,
                    confluenceConfigs,
                    grafanaConfigs,
                    jiraConfigs,
                    aiConfig,
                    githubRepoConfigPort,
                    mcpServerConfigPort,
                    StubUiPreferences(),
                    StubTextFilter(),
                    FixedClockPort(EXPORT_EPOCH),
                ),
            configImport = configImport,
            workspaceFiles = workspaceFiles,
            i18n = i18n,
            exportDtoMapper = ConfigExportDtoMapper(),
            importMapper = ConfigImportMapper(),
            envelopeCodec = ConfigEnvelopeCodec(objectMapper, ConfigEnvelopeService(ConfigEnvelopeCipherAdapter(objectMapper))),
        )
    }

    @Test
    fun `importConfigFromPath returns 400 when path is a directory`() {
        val dir = java.io.File.createTempFile("dir-test", "")
        dir.delete()
        dir.mkdirs()
        try {
            whenever(i18n.t("config.be_file_not_found")).thenReturn("File not found")
            val req = ImportFromPathRequest(path = dir.absolutePath)
            val result = controller.importConfigFromPath(req)
            assertEquals(400, result.statusCode.value())
        } finally {
            dir.deleteRecursively()
        }
    }
}

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImportExportConfigApiControllerEmptyPasswordTest {
    @Mock private lateinit var gitlabConfigs: GitlabConfigReadPort

    @Mock private lateinit var databaseConfigs: DatabaseConfigReadPort

    @Mock private lateinit var confluenceConfigs: ConfluenceConfigReadPort

    @Mock private lateinit var grafanaConfigs: GrafanaConfigReadPort

    @Mock private lateinit var jiraConfigs: JiraConfigReadPort

    @Mock private lateinit var aiConfig: AiConfigReadPort

    @Mock private lateinit var configImport: ConfigImportUseCase

    @Mock private lateinit var i18n: I18nUseCase

    @Mock private lateinit var githubRepoConfigPort: GithubRepoConfigPort

    @Mock private lateinit var mcpServerConfigPort: McpServerConfigPort

    private val workspaceFiles: WorkspaceFileUseCase =
        spy(WorkspaceFileService(JavaFileSystemAdapter(), JavaFileSystemAdapter(), JavaFileSystemAdapter(), JavaFileSystemAdapter()))

    private val objectMapper = ObjectMapper()

    private val controller by lazy {
        ImportExportConfigApiController(
            configExport =
                ConfigExportService(
                    gitlabConfigs,
                    databaseConfigs,
                    confluenceConfigs,
                    grafanaConfigs,
                    jiraConfigs,
                    aiConfig,
                    githubRepoConfigPort,
                    mcpServerConfigPort,
                    StubUiPreferences(),
                    StubTextFilter(),
                    FixedClockPort(EXPORT_EPOCH),
                ),
            configImport = configImport,
            workspaceFiles = workspaceFiles,
            i18n = i18n,
            exportDtoMapper = ConfigExportDtoMapper(),
            importMapper = ConfigImportMapper(),
            envelopeCodec = ConfigEnvelopeCodec(objectMapper, ConfigEnvelopeService(ConfigEnvelopeCipherAdapter(objectMapper))),
        )
    }

    @Test
    fun `importConfig with blank password is treated as null`() {
        val plainConfig = """{"version": 1, "gitlab": [], "databases": [], "confluence": [], "grafana": [], "jira": []}"""
        val file = MockMultipartFile("file", "config.json", "application/json", plainConfig.toByteArray())
        whenever(configImport.importConfigs(any(), any())).thenReturn(emptyMap())
        val result = controller.importConfig(file, "   ")
        assertEquals(200, result.statusCode.value())
    }
}
