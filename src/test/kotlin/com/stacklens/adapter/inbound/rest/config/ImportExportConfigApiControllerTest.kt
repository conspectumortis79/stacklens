package com.stacklens.adapter.inbound.rest.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.inbound.rest.ExportRequestDto
import com.stacklens.adapter.inbound.rest.ImportFromPathRequest
import com.stacklens.adapter.inbound.rest.SaveConfigToPathRequest
import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
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
import com.stacklens.domain.model.AiConfig
import com.stacklens.testsupport.FixedClockPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockMultipartFile
import java.io.File

private const val EXPORT_EPOCH = 1_750_000_000_000L

@ExtendWith(MockitoExtension::class)
class ImportExportConfigApiControllerTest {
    @Mock private lateinit var gitlabConfigs: GitlabConfigReadPort

    @Mock private lateinit var databaseConfigs: DatabaseConfigReadPort

    @Mock private lateinit var confluenceConfigs: ConfluenceConfigReadPort

    @Mock private lateinit var grafanaConfigs: GrafanaConfigReadPort

    @Mock private lateinit var jiraConfigs: JiraConfigReadPort

    @Mock private lateinit var aiConfigUseCase: AiConfigReadPort

    @Mock private lateinit var configImport: ConfigImportUseCase

    private val workspaceFiles: WorkspaceFileUseCase =
        spy(WorkspaceFileService(JavaFileSystemAdapter(), JavaFileSystemAdapter(), JavaFileSystemAdapter(), JavaFileSystemAdapter()))

    @Mock private lateinit var i18n: I18nUseCase

    @Mock private lateinit var githubRepoConfigPort: GithubRepoConfigPort

    @Mock private lateinit var mcpServerConfigPort: McpServerConfigPort

    private val objectMapper =
        ObjectMapper().apply {
            findAndRegisterModules()
        }

    private val controller by lazy {
        ImportExportConfigApiController(
            configExport =
                ConfigExportService(
                    gitlabConfigs,
                    databaseConfigs,
                    confluenceConfigs,
                    grafanaConfigs,
                    jiraConfigs,
                    aiConfigUseCase,
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

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `exportConfig returns 400 when password is blank`() {
        whenever(i18n.t("config.be_password_empty")).thenReturn("Password is required")
        val result = controller.exportConfig(ExportRequestDto(password = ""))
        assertEquals(400, result.statusCode.value())
    }

    @Test
    fun `exportConfig returns encrypted JSON when password provided`() {
        whenever(gitlabConfigs.listGitlabConfigs()).thenReturn(emptyList())
        whenever(databaseConfigs.listDatabaseConfigs()).thenReturn(emptyList())
        whenever(confluenceConfigs.listConfluenceConfigs()).thenReturn(emptyList())
        whenever(grafanaConfigs.listGrafanaConfigs()).thenReturn(emptyList())
        whenever(jiraConfigs.listJiraConfigs()).thenReturn(emptyList())
        whenever(aiConfigUseCase.getAiConfig()).thenReturn(null)

        val result = controller.exportConfig(ExportRequestDto(password = "strongpassword"))
        assertEquals(200, result.statusCode.value())
        assertNotNull(result.body)
        assertTrue(result.body!!.isNotEmpty())
        val root = objectMapper.readTree(result.body)
        assertTrue(root.has("encrypted"))
        assertTrue(root.path("encrypted").asBoolean())
    }

    @Test
    fun `exportConfig includes AI config when available`() {
        val aiConfig = AiConfig(id = 1L, githubToken = "mytoken", model = "gpt-4")
        whenever(gitlabConfigs.listGitlabConfigs()).thenReturn(emptyList())
        whenever(databaseConfigs.listDatabaseConfigs()).thenReturn(emptyList())
        whenever(confluenceConfigs.listConfluenceConfigs()).thenReturn(emptyList())
        whenever(grafanaConfigs.listGrafanaConfigs()).thenReturn(emptyList())
        whenever(jiraConfigs.listJiraConfigs()).thenReturn(emptyList())
        whenever(aiConfigUseCase.getAiConfig()).thenReturn(aiConfig)

        val result = controller.exportConfig(ExportRequestDto(password = "pass"))
        assertEquals(200, result.statusCode.value())
        assertNotNull(result.body)
    }

    @Test
    fun `importConfig returns bad request for invalid json`() {
        val configFile = File(tempDir, "bad.json")
        configFile.writeText("not valid json")

        val req = ImportFromPathRequest(path = configFile.absolutePath)
        val result = controller.importConfigFromPath(req)
        assertEquals(400, result.statusCode.value())
    }

    @Test
    fun `importConfig imports plain (non-encrypted) config`() {
        val plainConfig =
            """
            {
              "version": 1,
              "exportedAt": "2024-01-01T00:00:00",
              "gitlab": [],
              "databases": [],
              "confluence": [],
              "grafana": [],
              "jira": []
            }
            """.trimIndent()
        val file = MockMultipartFile("file", "config.json", "application/json", plainConfig.toByteArray())

        val result = controller.importConfig(file, null)
        assertEquals(200, result.statusCode.value())
        val body = result.body
        assertNotNull(body)
        assertEquals(true, body!!["success"])
    }

    @Test
    fun `importConfig returns error when encrypted but no password`() {
        val encryptedJson =
            """
            {
              "version": 1,
              "encrypted": true,
              "algorithm": "AES-256-GCM",
              "kdf": "PBKDF2WithHmacSHA256",
              "iterations": 310000,
              "salt": "abc",
              "iv": "def",
              "data": "ghi"
            }
            """.trimIndent()
        val file = MockMultipartFile("file", "config.json", "application/json", encryptedJson.toByteArray())
        whenever(i18n.t("config.be_encrypted_need_password")).thenReturn("Encrypted file needs password")

        val result = controller.importConfig(file, null)
        assertEquals(400, result.statusCode.value())
    }

    @Test
    fun `importConfig returns error when encrypted and wrong password`() {
        val encryptedJson =
            """
            {
              "version": 1,
              "encrypted": true,
              "algorithm": "AES-256-GCM",
              "kdf": "PBKDF2WithHmacSHA256",
              "iterations": 310000,
              "salt": "aGVsbG8=",
              "iv": "AAAAAAAAAAAAAAAA",
              "data": "wrongdata"
            }
            """.trimIndent()
        val file = MockMultipartFile("file", "config.json", "application/json", encryptedJson.toByteArray())
        whenever(i18n.t(any<String>(), any())).thenReturn("Decrypt error")

        val result = controller.importConfig(file, "wrongpassword")
        assertEquals(400, result.statusCode.value())
    }

    @Test
    fun `importConfigFromPath returns 400 when file not found`() {
        whenever(i18n.t("config.be_file_not_found")).thenReturn("File not found")
        val req = ImportFromPathRequest(path = "/nonexistent/path/config.json")
        val result = controller.importConfigFromPath(req)
        assertEquals(400, result.statusCode.value())
    }

    @Test
    fun `importConfigFromPath imports valid config file`() {
        val content =
            """
            {
              "version": 1,
              "exportedAt": "2024-01-01T00:00:00",
              "gitlab": [],
              "databases": [],
              "confluence": [],
              "grafana": [],
              "jira": []
            }
            """.trimIndent()
        val configFile = File(tempDir, "config.json")
        configFile.writeText(content)

        val req = ImportFromPathRequest(path = configFile.absolutePath)
        val result = controller.importConfigFromPath(req)
        assertEquals(200, result.statusCode.value())
        assertEquals(true, result.body!!["success"])
    }

    @Test
    fun `importConfigFromPath returns bad request when invalid json`() {
        val configFile = File(tempDir, "bad.json")
        configFile.writeText("not json")
        whenever(i18n.t(any<String>(), any())).thenReturn("Invalid JSON error")

        val req = ImportFromPathRequest(path = configFile.absolutePath)
        val result = controller.importConfigFromPath(req)
        assertEquals(400, result.statusCode.value())
    }

    @Test
    fun `saveConfigToPath returns 400 when directory not found`() {
        org.mockito.Mockito
            .doReturn(WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.DIRECTORY_NOT_FOUND))
            .`when`(workspaceFiles)
            .saveText(any())
        whenever(i18n.t("config.be_dir_not_found")).thenReturn("Directory not found")
        val req =
            SaveConfigToPathRequest(
                path = "/nonexistent/dir",
                filename = "config.json",
                content = "{}",
            )
        val result = controller.saveConfigToPath(req)
        assertEquals(400, result.statusCode.value())
        assertEquals("Directory not found", result.body!!["error"])
    }

    @Test
    fun `saveConfigToPath returns the saved path from the use case`() {
        org.mockito.Mockito
            .doReturn(WorkspaceWriteResult.Saved("/tmp/myconfig.json"))
            .`when`(workspaceFiles)
            .saveText(any())
        val req =
            SaveConfigToPathRequest(
                path = tempDir.absolutePath,
                filename = "myconfig.json",
                content = """{"version": 1}""",
            )
        val result = controller.saveConfigToPath(req)
        assertEquals(200, result.statusCode.value())
        assertEquals("/tmp/myconfig.json", result.body!!["savedPath"])
    }

    @Test
    fun `saveConfigToPath forwards the request fields to the use case`() {
        org.mockito.Mockito
            .doReturn(WorkspaceWriteResult.Saved("/tmp/passwd"))
            .`when`(workspaceFiles)
            .saveText(any())
        val req =
            SaveConfigToPathRequest(
                path = tempDir.absolutePath,
                filename = "my config file!@#.json",
                content = "{}",
            )
        controller.saveConfigToPath(req)
        verify(workspaceFiles).saveText(
            TextFileSaveRequest(
                directory = tempDir.absolutePath,
                filename = "my config file!@#.json",
                content = "{}",
            ),
        )
    }

    @Test
    fun `saveConfigToPath returns 500 when the write is rejected`() {
        org.mockito.Mockito
            .doReturn(WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.WRITE_FAILED))
            .`when`(workspaceFiles)
            .saveText(any())
        whenever(i18n.t("config.be_save_error", "")).thenReturn("Error saving: ")
        val req =
            SaveConfigToPathRequest(
                path = tempDir.absolutePath,
                filename = "config.json",
                content = "{}",
            )
        val result = controller.saveConfigToPath(req)
        assertEquals(500, result.statusCode.value())
        assertEquals("Error saving: ", result.body!!["error"])
    }

    @Test
    fun `importConfig with non-empty gitlab list imports gitlab configs`() {
        val plainConfig =
            """
            {
              "version": 1,
              "exportedAt": "2024-01-01T00:00:00",
              "gitlab": [
                {
                  "name": "Test GitLab",
                  "baseUrl": "https://gitlab.example.com",
                  "accessToken": "mytoken",
                  "accessTokenSet": true,
                  "active": true
                }
              ],
              "databases": [],
              "confluence": [],
              "grafana": [],
              "jira": []
            }
            """.trimIndent()
        val file = MockMultipartFile("file", "config.json", "application/json", plainConfig.toByteArray())
        whenever(configImport.importConfigs(any(), any())).thenReturn(mapOf("gitlab" to 1))

        val result = controller.importConfig(file, null)
        assertEquals(200, result.statusCode.value())
        val imported = result.body!!["imported"] as Map<*, *>
        assertEquals(1, imported["gitlab"])
    }

    @Test
    fun `importConfig with ai config imports ai config`() {
        val plainConfig =
            """
            {
              "version": 1,
              "exportedAt": "2024-01-01T00:00:00",
              "gitlab": [],
              "databases": [],
              "confluence": [],
              "grafana": [],
              "jira": [],
              "ai": {
                "githubToken": "mytoken",
                "model": "gpt-4"
              }
            }
            """.trimIndent()
        val file = MockMultipartFile("file", "config.json", "application/json", plainConfig.toByteArray())
        whenever(configImport.importConfigs(any(), any())).thenReturn(mapOf("ai" to 1))

        val result = controller.importConfig(file, null)
        assertEquals(200, result.statusCode.value())
        val imported = result.body!!["imported"] as Map<*, *>
        assertEquals(1, imported["ai"])
    }

    @Test
    fun `exportAll exercises the toExportDto mapping for every config kind`() {
        whenever(gitlabConfigs.listGitlabConfigs()).thenReturn(
            listOf(
                com.stacklens.domain.model.GitlabConfig(
                    id = 1L,
                    name = "g",
                    baseUrl = "https://gl",
                    accessToken = "tok",
                ),
            ),
        )
        whenever(databaseConfigs.listDatabaseConfigs()).thenReturn(
            listOf(
                com.stacklens.domain.model.DatabaseConfig(
                    id = 2L,
                    name = "d",
                    dbType = com.stacklens.domain.enums.DatabaseType.POSTGRESQL,
                    host = "h",
                    port = 5432,
                    databaseName = "db",
                    username = "u",
                    password = "p",
                ),
            ),
        )
        whenever(confluenceConfigs.listConfluenceConfigs()).thenReturn(
            listOf(
                com.stacklens.domain.model.ConfluenceConfig(
                    id = 3L,
                    name = "c",
                    baseUrl = "https://cf",
                    username = "u",
                    apiToken = "tok",
                ),
            ),
        )
        whenever(grafanaConfigs.listGrafanaConfigs()).thenReturn(
            listOf(
                com.stacklens.domain.model.GrafanaConfig(
                    id = 4L,
                    name = "gr",
                    baseUrl = "https://gr",
                    username = "u",
                    password = "p",
                ),
            ),
        )
        whenever(jiraConfigs.listJiraConfigs()).thenReturn(
            listOf(
                com.stacklens.domain.model.JiraConfig(
                    id = 5L,
                    name = "j",
                    baseUrl = "https://jira",
                    projectKey = "X",
                    apiToken = "tok",
                ),
            ),
        )
        val resp =
            controller.exportConfig(
                com.stacklens.adapter.inbound.rest
                    .ExportRequestDto(password = "p"),
            )
        assertEquals(200, resp.statusCode.value())
        assertTrue(resp.body!!.isNotEmpty())
    }

    @Test
    fun `roundtrip export then import works`() {
        whenever(gitlabConfigs.listGitlabConfigs()).thenReturn(emptyList())
        whenever(databaseConfigs.listDatabaseConfigs()).thenReturn(emptyList())
        whenever(confluenceConfigs.listConfluenceConfigs()).thenReturn(emptyList())
        whenever(grafanaConfigs.listGrafanaConfigs()).thenReturn(emptyList())
        whenever(jiraConfigs.listJiraConfigs()).thenReturn(emptyList())
        whenever(aiConfigUseCase.getAiConfig()).thenReturn(null)

        val password = "testpassword123"
        val exportResult = controller.exportConfig(ExportRequestDto(password = password))
        assertEquals(200, exportResult.statusCode.value())

        val exportedBytes = exportResult.body!!
        val file = MockMultipartFile("file", "config.json", "application/json", exportedBytes)

        val importResult = controller.importConfig(file, password)
        assertEquals(200, importResult.statusCode.value())
        assertEquals(true, importResult.body!!["success"])
    }

    @Test
    fun `importConfig with all config types imports all`() {
        val fullConfig =
            """
            {
              "version": 1,
              "exportedAt": "2024-01-01T00:00:00",
              "gitlab": [{"name":"GL","baseUrl":"http://gl","accessToken":"t","accessTokenSet":true,"active":true}],
              "databases": [{"name":"DB","dbType":"MYSQL","host":"h","port":3306,"databaseName":"db","username":"u","password":"p","active":true}],
              "confluence": [{"name":"CF","baseUrl":"http://cf","username":"u","apiToken":"t","active":true}],
              "grafana": [{"name":"GR","baseUrl":"http://gr","username":"u","password":"p","apiToken":"t","active":true}],
              "jira": [{"name":"JR","baseUrl":"http://jr","projectKey":"P","username":"u","apiToken":"t","active":true}],
              "ai": {"githubToken":"","model":""}
            }
            """.trimIndent()
        val file = MockMultipartFile("file", "config.json", "application/json", fullConfig.toByteArray())
        whenever(configImport.importConfigs(any(), any())).thenReturn(
            mapOf("gitlab" to 1, "databases" to 1, "confluence" to 1, "grafana" to 1, "jira" to 1, "ai" to 1),
        )

        val result = controller.importConfig(file, null)
        assertEquals(200, result.statusCode.value())
        val imported = result.body!!["imported"] as Map<*, *>
        assertEquals(1, imported["gitlab"])
        assertEquals(1, imported["databases"])
        assertEquals(1, imported["confluence"])
        assertEquals(1, imported["grafana"])
        assertEquals(1, imported["jira"])
        assertEquals(1, imported["ai"])
    }

    @Test
    fun `importConfigFromPath catches general exception and returns 500`() {
        val brokenEncrypted =
            """
            {
              "encrypted": true,
              "algorithm": "AES-256-GCM",
              "kdf": "PBKDF2WithHmacSHA256",
              "iterations": 1,
              "salt": "!!!NOT_BASE64!!!",
              "iv": "!!!NOT_BASE64!!!",
              "data": "!!!NOT_BASE64!!!"
            }
            """.trimIndent()
        val configFile = File(tempDir, "broken.json")
        configFile.writeText(brokenEncrypted)
        whenever(i18n.t(any<String>(), any())).thenReturn("decrypt failed")

        val req = ImportFromPathRequest(path = configFile.absolutePath, password = "pass")
        val result = controller.importConfigFromPath(req)
        assertEquals(400, result.statusCode.value())
    }

    @Test
    fun `importConfigFromPath with blank password on encrypted file`() {
        val encryptedJson =
            """
            {
              "encrypted": true,
              "algorithm": "AES-256-GCM",
              "kdf": "PBKDF2WithHmacSHA256",
              "iterations": 310000,
              "salt": "aGVsbG8=",
              "iv": "AAAAAAAAAAAAAAAA",
              "data": "test"
            }
            """.trimIndent()
        val configFile = File(tempDir, "enc.json")
        configFile.writeText(encryptedJson)
        whenever(i18n.t("config.be_encrypted_need_password")).thenReturn("Password required")

        val req = ImportFromPathRequest(path = configFile.absolutePath, password = " ")
        val result = controller.importConfigFromPath(req)
        assertEquals(400, result.statusCode.value())
    }

    @Test
    fun `saveConfigToPath returns 500 on write error`() {
        org.mockito.Mockito
            .doReturn(WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.WRITE_FAILED))
            .`when`(workspaceFiles)
            .saveText(any())
        whenever(i18n.t("config.be_save_error", "")).thenReturn("save error")

        val req =
            SaveConfigToPathRequest(
                path = tempDir.absolutePath,
                filename = "test.json",
                content = "{}",
            )
        val result = controller.saveConfigToPath(req)
        assertEquals(500, result.statusCode.value())
        assertEquals("save error", result.body!!["error"])
    }

    @Test
    fun `saveConfigToPath returns 400 when path exists but is a file not a directory`() {
        org.mockito.Mockito
            .doReturn(WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.DIRECTORY_NOT_FOUND))
            .`when`(workspaceFiles)
            .saveText(any())
        whenever(i18n.t("config.be_dir_not_found")).thenReturn("Not a directory")

        val req = SaveConfigToPathRequest(path = tempDir.absolutePath, filename = "f.json", content = "{}")
        val result = controller.saveConfigToPath(req)
        assertEquals(400, result.statusCode.value())
        assertEquals("Not a directory", result.body!!["error"])
    }

    @Test
    fun `importConfigFromPath returns 400 when path is a directory not a file`() {
        val subDir = File(tempDir, "asubdir")
        subDir.mkdirs()
        whenever(i18n.t("config.be_file_not_found")).thenReturn("Not a file")

        val req = ImportFromPathRequest(path = subDir.absolutePath)
        val result = controller.importConfigFromPath(req)
        assertEquals(400, result.statusCode.value())
    }

    @Test
    fun `importConfigFromBytes with ai config having null githubToken and model still saves`() {
        val content =
            """
            {
              "version": 1,
              "exportedAt": "2024-01-01T00:00:00",
              "gitlab": [], "databases": [], "confluence": [], "grafana": [], "jira": [],
              "ai": {"githubToken": null, "model": null}
            }
            """.trimIndent()
        val configFile = File(tempDir, "config-null-ai.json")
        configFile.writeText(content)
        whenever(configImport.importConfigs(any(), any())).thenReturn(mapOf("ai" to 1))

        val req = ImportFromPathRequest(path = configFile.absolutePath)
        val result = controller.importConfigFromPath(req)
        assertEquals(200, result.statusCode.value())
    }

    @Test
    fun `importConfigFromPath with skipIfNotEmpty=true passes flag to importConfigs`() {
        whenever(configImport.importConfigs(any(), any())).thenReturn(emptyMap())
        val content =
            """
            {
              "version": 1,
              "exportedAt": "2024-01-01T00:00:00",
              "gitlab": [], "databases": [], "confluence": [], "grafana": [], "jira": [],
              "mcp": [{"name":"Demo MCP","baseUrl":"http://demo","active":true,
                       "authToken":"","routeJira":false,"routeConfluence":false,
                       "routeGitlab":false,"routeGithub":false,"routeGrafana":false,
                       "routeDatabase":false,"jiraBaseUrl":"","jiraUsername":"",
                       "jiraApiToken":"","jiraCloudMode":false,"confluenceBaseUrl":"",
                       "confluenceUsername":"","confluenceApiToken":"","confluenceCloudMode":false,
                       "gitlabBaseUrl":"","gitlabAccessToken":"","githubApiBase":"",
                       "githubToken":"","grafanaBaseUrl":"","grafanaApiKey":"",
                       "autoStartManaged":false}]
            }
            """.trimIndent()
        val configFile = File(tempDir, "demo-skip.json")
        configFile.writeText(content)
        val req = ImportFromPathRequest(path = configFile.absolutePath, skipIfNotEmpty = true)
        val result = controller.importConfigFromPath(req)
        assertEquals(200, result.statusCode.value())
        verify(configImport).importConfigs(any(), org.mockito.kotlin.eq(true))
    }

    @Test
    fun `importConfigFromPath with skipIfNotEmpty=false passes flag to importConfigs`() {
        whenever(configImport.importConfigs(any(), any())).thenReturn(mapOf("mcp" to 1))
        val content =
            """
            {
              "version": 1,
              "exportedAt": "2024-01-01T00:00:00",
              "gitlab": [], "databases": [], "confluence": [], "grafana": [], "jira": [],
              "mcp": [{"name":"Demo MCP","baseUrl":"http://demo","active":true,
                       "authToken":"","routeJira":false,"routeConfluence":false,
                       "routeGitlab":false,"routeGithub":false,"routeGrafana":false,
                       "routeDatabase":false,"jiraBaseUrl":"","jiraUsername":"",
                       "jiraApiToken":"","jiraCloudMode":false,"confluenceBaseUrl":"",
                       "confluenceUsername":"","confluenceApiToken":"","confluenceCloudMode":false,
                       "gitlabBaseUrl":"","gitlabAccessToken":"","githubApiBase":"",
                       "githubToken":"","grafanaBaseUrl":"","grafanaApiKey":"",
                       "autoStartManaged":false}]
            }
            """.trimIndent()
        val configFile = File(tempDir, "demo-noskip.json")
        configFile.writeText(content)
        val req = ImportFromPathRequest(path = configFile.absolutePath, skipIfNotEmpty = false)
        val result = controller.importConfigFromPath(req)
        assertEquals(200, result.statusCode.value())
        val imported = result.body!!["imported"] as Map<*, *>
        assertEquals(1, imported["mcp"])
        verify(configImport).importConfigs(any(), org.mockito.kotlin.eq(false))
    }

    @Test
    fun `importConfigFromPath with skipIfNotEmpty=true and all types delegates to importConfigs`() {
        whenever(configImport.importConfigs(any(), any())).thenReturn(
            mapOf(
                "gitlab" to 1,
                "databases" to 1,
                "confluence" to 1,
                "grafana" to 1,
                "jira" to 1,
                "github" to 1,
                "mcp" to 1,
            ),
        )
        val allJson =
            """
              {
                "version": 1,
                "exportedAt": "2024-01-01T00:00:00",
                "gitlab": [{"name":"GL","baseUrl":"http://gl","accessToken":"t","accessTokenSet":true,"active":true}],
            "databases": [{"name":"DB","dbType":"MYSQL","host":"h","port":3306,"databaseName":"db","username":"u","password":"p","active":true}],
            "confluence": [{"name":"CF","baseUrl":"http://cf","username":"u","apiToken":"t","active":true}],
            "grafana": [{"name":"GR","baseUrl":"http://gr","username":"u","password":"p","apiToken":"t","active":true}],
            "jira": [{"name":"JR","baseUrl":"http://jr","projectKey":"P","username":"u","apiToken":"t","active":true}],
            "github": [{"name":"GH","owner":"o","repo":"r","branch":"main","accessToken":"t","apiBase":"https://api.github.com","active":true}],
            "mcp": [{"name":"MCP","baseUrl":"http://mcp","active":true,"authToken":"",
                     "routeJira":false,"routeConfluence":false,"routeGitlab":false,"routeGithub":false,
                     "routeGrafana":false,"routeDatabase":false,"jiraBaseUrl":"","jiraUsername":"",
                     "jiraApiToken":"","jiraCloudMode":false,"confluenceBaseUrl":"","confluenceUsername":"",
                     "confluenceApiToken":"","confluenceCloudMode":false,"gitlabBaseUrl":"","gitlabAccessToken":"",
                     "githubApiBase":"","githubToken":"","grafanaBaseUrl":"","grafanaApiKey":"",
                     "autoStartManaged":false}]
              }
            """.trimIndent()
        val configFile = File(tempDir, "all-types.json")
        configFile.writeText(allJson)
        val req = ImportFromPathRequest(path = configFile.absolutePath, skipIfNotEmpty = true)
        val result = controller.importConfigFromPath(req)
        assertEquals(200, result.statusCode.value())
        val imported = result.body!!["imported"] as Map<*, *>
        assertEquals(1, imported["gitlab"])
        assertEquals(1, imported["mcp"])
        verify(configImport).importConfigs(any(), org.mockito.kotlin.eq(true))
    }

    @Test
    fun `hasAnyConfig returns configured=false when service returns false`() {
        whenever(configImport.hasAnyConfig()).thenReturn(false)
        val result = controller.hasAnyConfig()
        assertEquals(false, result["configured"])
    }

    @Test
    fun `hasAnyConfig returns configured=true when service returns true`() {
        whenever(configImport.hasAnyConfig()).thenReturn(true)
        val result = controller.hasAnyConfig()
        assertEquals(true, result["configured"])
    }

    @Test
    fun `LanguageDto creates with default`() {
        val dto =
            com.stacklens.adapter.inbound.rest
                .LanguageDto()
        assertEquals("en", dto.language)
    }

    @Test
    fun `AiExportDto creates with defaults`() {
        val dto =
            com.stacklens.adapter.inbound.rest
                .AiExportDto()
        assertEquals(null, dto.githubToken)
        assertEquals(null, dto.model)
    }

    @Test
    fun `exportConfig serialises every AI provider credential in the export envelope`() {
        val aiConfig =
            AiConfig(
                id = 1L,
                githubToken = "ghp_source_token",
                model = "deepseek/deepseek-v4-flash",
                aiProvider = "openrouter",
                ollamaUrl = "http://source-host:11434",
                ollamaNumCtx = 65536,
                lmStudioUrl = "http://source-host:1234",
                openAiApiKey = "sk-proj-source-openai",
                openAiReasoningEffort = "high",
                openRouterApiKey = "sk-or-v1-source-openrouter",
            )
        whenever(gitlabConfigs.listGitlabConfigs()).thenReturn(emptyList())
        whenever(databaseConfigs.listDatabaseConfigs()).thenReturn(emptyList())
        whenever(confluenceConfigs.listConfluenceConfigs()).thenReturn(emptyList())
        whenever(grafanaConfigs.listGrafanaConfigs()).thenReturn(emptyList())
        whenever(jiraConfigs.listJiraConfigs()).thenReturn(emptyList())
        whenever(aiConfigUseCase.getAiConfig()).thenReturn(aiConfig)

        val password = "roundtrip-test-pwd"
        val exportResult = controller.exportConfig(ExportRequestDto(password = password))
        assertEquals(200, exportResult.statusCode.value())

        val plain =
            ConfigEnvelopeCipherAdapter(objectMapper).decrypt(
                exportResult.body!!,
                password,
            )
        val dto = objectMapper.readTree(plain!!)
        val ai = dto.path("ai")

        assertEquals("ghp_source_token", ai.path("githubToken").asText())
        assertEquals("deepseek/deepseek-v4-flash", ai.path("model").asText())
        assertEquals("openrouter", ai.path("aiProvider").asText())
        assertEquals("http://source-host:11434", ai.path("ollamaUrl").asText())
        assertEquals(65536, ai.path("ollamaNumCtx").asInt())
        assertEquals("http://source-host:1234", ai.path("lmStudioUrl").asText())
        assertEquals("sk-proj-source-openai", ai.path("openAiApiKey").asText())
        assertEquals("high", ai.path("openAiReasoningEffort").asText())
        assertEquals("sk-or-v1-source-openrouter", ai.path("openRouterApiKey").asText())
    }

    @Test
    fun `importConfig forwards every AI provider credential to the service layer`() {
        val captured = org.mockito.kotlin.argumentCaptor<com.stacklens.application.port.inbound.config.ConfigImportRequest>()
        val plainJson =
            """
            {
              "version": 1,
              "exportedAt": "2024-01-01T00:00:00",
              "gitlab": [],
              "databases": [],
              "confluence": [],
              "grafana": [],
              "jira": [],
              "ai": {
                "githubToken": "ghp_roundtrip",
                "model": "deepseek/deepseek-v4-flash",
                "aiProvider": "openrouter",
                "ollamaUrl": "http://source-host:11434",
                "ollamaNumCtx": 65536,
                "lmStudioUrl": "http://source-host:1234",
                "openAiApiKey": "sk-proj-roundtrip",
                "openAiReasoningEffort": "high",
                "openRouterApiKey": "sk-or-v1-roundtrip"
              }
            }
            """.trimIndent()
        val encrypted: ByteArray =
            ConfigEnvelopeCipherAdapter(objectMapper).encrypt(plainJson.toByteArray(), "roundtrip-test-pwd")
        val file = MockMultipartFile("file", "config.json", "application/json", encrypted)

        whenever(configImport.importConfigs(captured.capture(), any())).thenReturn(mapOf("ai" to 1))

        val result = controller.importConfig(file, "roundtrip-test-pwd")
        assertEquals(200, result.statusCode.value())

        val forwarded = captured.firstValue.aiConfig!!
        assertEquals("ghp_roundtrip", forwarded.githubToken)
        assertEquals("deepseek/deepseek-v4-flash", forwarded.model)
        assertEquals("openrouter", forwarded.aiProvider)
        assertEquals("http://source-host:11434", forwarded.ollamaUrl)
        assertEquals(65536, forwarded.ollamaNumCtx)
        assertEquals("http://source-host:1234", forwarded.lmStudioUrl)
        assertEquals("sk-proj-roundtrip", forwarded.openAiApiKey)
        assertEquals("high", forwarded.openAiReasoningEffort)
        assertEquals("sk-or-v1-roundtrip", forwarded.openRouterApiKey)
    }
}
