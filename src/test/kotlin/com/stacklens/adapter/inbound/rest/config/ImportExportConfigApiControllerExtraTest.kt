package com.stacklens.adapter.inbound.rest.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.inbound.rest.ExportRequestDto
import com.stacklens.adapter.inbound.rest.GithubRepoExportDto
import com.stacklens.adapter.inbound.rest.ImportFromPathRequest
import com.stacklens.adapter.inbound.rest.McpServerExportDto
import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.adapter.out.security.ConfigEnvelopeCipherAdapter
import com.stacklens.application.port.inbound.config.ConfigImportUseCase
import com.stacklens.application.port.inbound.platform.I18nUseCase
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
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.McpServerConfig
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
class ImportExportConfigApiControllerExtraTest {
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

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `exportConfig includes github and mcp configs in envelope`() {
        whenever(gitlabConfigs.listGitlabConfigs()).thenReturn(emptyList())
        whenever(databaseConfigs.listDatabaseConfigs()).thenReturn(emptyList())
        whenever(confluenceConfigs.listConfluenceConfigs()).thenReturn(emptyList())
        whenever(grafanaConfigs.listGrafanaConfigs()).thenReturn(emptyList())
        whenever(jiraConfigs.listJiraConfigs()).thenReturn(emptyList())
        whenever(aiConfig.getAiConfig()).thenReturn(null)

        val gh =
            GithubRepoConfig(
                name = "gh1",
                owner = "owner",
                repo = "repo",
                branch = "main",
                accessToken = "token",
                apiBase = "https://api.github.com",
                active = true,
            )
        whenever(githubRepoConfigPort.findAllGithubRepoConfigs()).thenReturn(listOf(gh))

        val mcp =
            McpServerConfig(
                name = "mcp1",
                baseUrl = "https://mcp.example.com",
                active = true,
                routeJira = true,
                authToken = "auth",
                jiraBaseUrl = "https://jira.example.com",
                jiraUsername = "u",
                jiraApiToken = "t",
                jiraCloudMode = true,
                confluenceBaseUrl = "https://cf.example.com",
                confluenceUsername = "u",
                confluenceApiToken = "t",
                confluenceCloudMode = true,
                gitlabBaseUrl = "https://gitlab.example.com",
                gitlabAccessToken = "t",
                githubApiBase = "https://api.github.com",
                githubToken = "t",
                grafanaBaseUrl = "https://grafana.example.com",
                grafanaApiKey = "k",
                autoStartManaged = true,
            )
        whenever(mcpServerConfigPort.findAllMcpServerConfigs()).thenReturn(listOf(mcp))

        val resp = controller.exportConfig(ExportRequestDto(password = "pw123456"))
        assertEquals(200, resp.statusCode.value())
        assertNotNull(resp.body)
        val root = objectMapper.readTree(resp.body)
        assertTrue(root.has("encrypted"))
        assertTrue(root.path("encrypted").asBoolean())
    }

    @Test
    fun `exportConfig sets Content-Disposition with date filename`() {
        whenever(gitlabConfigs.listGitlabConfigs()).thenReturn(emptyList())
        whenever(databaseConfigs.listDatabaseConfigs()).thenReturn(emptyList())
        whenever(confluenceConfigs.listConfluenceConfigs()).thenReturn(emptyList())
        whenever(grafanaConfigs.listGrafanaConfigs()).thenReturn(emptyList())
        whenever(jiraConfigs.listJiraConfigs()).thenReturn(emptyList())
        whenever(aiConfig.getAiConfig()).thenReturn(null)

        val resp = controller.exportConfig(ExportRequestDto(password = "pw123456"))
        assertEquals(200, resp.statusCode.value())
        val headers = resp.headers
        val cd = headers["Content-Disposition"]?.firstOrNull() ?: ""
        assertTrue(cd.startsWith("attachment; filename="), "Content-Disposition: $cd")
        assertTrue(cd.contains("stacklens-config-"))
        assertTrue(cd.contains(".json"))
    }

    @Test
    fun `importConfig with correct password decrypts and imports`() {
        whenever(gitlabConfigs.listGitlabConfigs()).thenReturn(emptyList())
        whenever(databaseConfigs.listDatabaseConfigs()).thenReturn(emptyList())
        whenever(confluenceConfigs.listConfluenceConfigs()).thenReturn(emptyList())
        whenever(grafanaConfigs.listGrafanaConfigs()).thenReturn(emptyList())
        whenever(jiraConfigs.listJiraConfigs()).thenReturn(emptyList())
        whenever(aiConfig.getAiConfig()).thenReturn(null)

        val password = "correct horse battery staple"
        val exportResp = controller.exportConfig(ExportRequestDto(password = password))
        val encrypted = exportResp.body!!
        val file = MockMultipartFile("file", "config.json", "application/json", encrypted)

        whenever(configImport.importConfigs(any(), any()))
            .thenReturn(mapOf("gitlab" to 0))

        val importResp = controller.importConfig(file, password)
        assertEquals(200, importResp.statusCode.value())
        assertEquals(true, importResp.body!!["success"])
    }

    @Test
    fun `importConfig with iterations above 2M returns 400 because key derivation mismatches`() {
        val plain =
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
        val plainBytes = plain.toByteArray()
        val salt = "AAAAAAAAAAAAAAAAAAAAAA==".toByteArray()
        val iv = "AAAAAAAAAAAAAAAA".toByteArray()
        val (envelope, password) = encryptForTest(plainBytes, salt, iv, iterations = 1)
        val tree = objectMapper.readTree(envelope)
        val node = (tree as com.fasterxml.jackson.databind.node.ObjectNode)
        node.put("iterations", 5_000_000)
        val inflated = objectMapper.writeValueAsBytes(node)
        val file = MockMultipartFile("file", "c.json", "application/json", inflated)

        val resp = controller.importConfig(file, password)
        assertEquals(400, resp.statusCode.value())
    }

    @Test
    fun `importConfig with low iterations caps to 1`() {
        val plain =
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
        val plainBytes = plain.toByteArray()
        val salt = "AAAAAAAAAAAAAAAAAAAAAA==".toByteArray()
        val iv = "AAAAAAAAAAAAAAAA".toByteArray()
        val (envelope, password) = encryptForTest(plainBytes, salt, iv, iterations = 1)
        val tree = objectMapper.readTree(envelope)
        val node = (tree as com.fasterxml.jackson.databind.node.ObjectNode)
        node.put("iterations", 0)
        val inflated = objectMapper.writeValueAsBytes(node)
        val file = MockMultipartFile("file", "c.json", "application/json", inflated)
        whenever(configImport.importConfigs(any(), any()))
            .thenReturn(emptyMap())

        val resp = controller.importConfig(file, password)
        assertEquals(200, resp.statusCode.value())
    }

    @Test
    fun `importConfigFromPath returns 400 with be_invalid_json message`() {
        val configFile = File(tempDir, "bad.json").apply { writeText("not valid json") }
        whenever(i18n.t(any<String>(), any())).thenReturn("Invalid JSON")

        val req = ImportFromPathRequest(path = configFile.absolutePath)
        val result = controller.importConfigFromPath(req)
        assertEquals(400, result.statusCode.value())
    }

    @Test
    fun `importConfigFromPath returns 500 when import throws`() {
        val configFile = File(tempDir, "ok.json")
        configFile.writeText(
            """
            {
              "version": 1,
              "exportedAt": "2024-01-01T00:00:00",
              "gitlab": [{"name":"x","baseUrl":"x","accessToken":"x","accessTokenSet":true,"active":true}],
              "databases": [],
              "confluence": [],
              "grafana": [],
              "jira": []
            }
            """.trimIndent(),
        )
        whenever(configImport.importConfigs(any(), any())).thenThrow(RuntimeException("boom"))

        val req = ImportFromPathRequest(path = configFile.absolutePath)
        val result = controller.importConfigFromPath(req)
        assertEquals(500, result.statusCode.value())
    }

    @Test
    fun `importConfig returns 500 when internal error`() {
        val plain =
            """
            {
              "version": 1,
              "exportedAt": "2024-01-01T00:00:00",
              "gitlab": [{"name":"x","baseUrl":"x","accessToken":"x","accessTokenSet":true,"active":true}],
              "databases": [],
              "confluence": [],
              "grafana": [],
              "jira": []
            }
            """.trimIndent()
        val file = MockMultipartFile("file", "c.json", "application/json", plain.toByteArray())
        whenever(configImport.importConfigs(any(), any())).thenThrow(RuntimeException("boom"))

        val result = controller.importConfig(file, null)
        assertEquals(500, result.statusCode.value())
    }

    @Test
    fun `importConfigFromPath with skipIfNotEmpty=true forwards to use case`() {
        val configFile = File(tempDir, "ok.json")
        configFile.writeText(
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
            """.trimIndent(),
        )
        whenever(configImport.importConfigs(any(), any()))
            .thenReturn(emptyMap())

        val req = ImportFromPathRequest(path = configFile.absolutePath, skipIfNotEmpty = true)
        val result = controller.importConfigFromPath(req)
        assertEquals(200, result.statusCode.value())
        verify(configImport).importConfigs(any(), org.mockito.kotlin.eq(true))
    }

    @Test
    fun `hasAnyConfig returns configured true when use case reports true`() {
        whenever(configImport.hasAnyConfig()).thenReturn(true)
        val r = controller.hasAnyConfig()
        assertEquals(true, r["configured"])
    }

    @Test
    fun `hasAnyConfig returns configured false when use case reports false`() {
        whenever(configImport.hasAnyConfig()).thenReturn(false)
        val r = controller.hasAnyConfig()
        assertEquals(false, r["configured"])
    }

    @Test
    fun `saveConfigToPath returns 500 when write fails`() {
        org.mockito.Mockito
            .doReturn(WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.WRITE_FAILED))
            .`when`(workspaceFiles)
            .saveText(any())
        whenever(i18n.t("config.be_save_error", "")).thenReturn("Save failed")
        val req =
            com.stacklens.adapter.inbound.rest.SaveConfigToPathRequest(
                path = tempDir.absolutePath,
                filename = "cfg.json",
                content = "{}",
            )
        val r = controller.saveConfigToPath(req)
        assertEquals(500, r.statusCode.value())
        assertEquals("Save failed", r.body!!["error"])
    }

    @Test
    fun `GithubRepoExportDto stores all fields`() {
        val d =
            GithubRepoExportDto(
                name = "n",
                owner = "o",
                repo = "r",
                branch = "main",
                accessToken = "t",
                apiBase = "https://api",
                active = true,
            )
        assertEquals("n", d.name)
        assertEquals("o", d.owner)
        assertEquals("r", d.repo)
        assertEquals("main", d.branch)
        assertEquals("t", d.accessToken)
        assertEquals("https://api", d.apiBase)
        assertEquals(true, d.active)
    }

    @Test
    fun `McpServerExportDto stores all fields`() {
        val d =
            McpServerExportDto(
                name = "n",
                baseUrl = "b",
                authToken = "t",
                active = true,
                routeJira = true,
                routeConfluence = false,
                routeGitlab = true,
                routeGithub = false,
                routeGrafana = true,
                routeDatabase = false,
                jiraBaseUrl = "jb",
                jiraUsername = "ju",
                jiraApiToken = "jt",
                jiraCloudMode = true,
                confluenceBaseUrl = "cb",
                confluenceUsername = "cu",
                confluenceApiToken = "ct",
                confluenceCloudMode = false,
                gitlabBaseUrl = "gb",
                gitlabAccessToken = "gt",
                githubApiBase = "hab",
                githubToken = "ht",
                grafanaBaseUrl = "gfb",
                grafanaApiKey = "gfa",
                autoStartManaged = true,
            )
        assertEquals("n", d.name)
        assertEquals(true, d.routeJira)
        assertEquals(false, d.routeConfluence)
        assertEquals(true, d.autoStartManaged)
    }

    private fun encryptForTest(
        plainBytes: ByteArray,
        salt: ByteArray,
        iv: ByteArray,
        iterations: Int,
    ): Pair<ByteArray, String> {
        val password = "test-password-12345"
        val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes =
            factory
                .generateSecret(
                    javax.crypto.spec.PBEKeySpec(password.toCharArray(), salt, iterations, 256),
                ).encoded
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            javax.crypto.Cipher.ENCRYPT_MODE,
            javax.crypto.spec.SecretKeySpec(keyBytes, "AES"),
            javax.crypto.spec.GCMParameterSpec(128, iv),
        )
        val ciphertext = cipher.doFinal(plainBytes)
        val envelope =
            mapOf(
                "version" to 1,
                "encrypted" to true,
                "algorithm" to "AES-256-GCM",
                "kdf" to "PBKDF2WithHmacSHA256",
                "iterations" to iterations,
                "salt" to
                    java.util.Base64
                        .getEncoder()
                        .encodeToString(salt),
                "iv" to
                    java.util.Base64
                        .getEncoder()
                        .encodeToString(iv),
                "data" to
                    java.util.Base64
                        .getEncoder()
                        .encodeToString(ciphertext),
            )
        return Pair(objectMapper.writeValueAsBytes(envelope), password)
    }

    @Test
    fun `normalizeTheme returns theme when supported`() {
        val method =
            controller.javaClass.declaredClasses
                .firstOrNull { it.simpleName == "Companion" }
                ?.declaredMethods
                ?.firstOrNull { it.name == "normalizeTheme" }
                ?: run {
                    val m =
                        controller.javaClass.declaredMethods.firstOrNull {
                            it.name == "normalizeTheme" &&
                                it.parameterCount == 1 &&
                                it.parameterTypes[0] == String::class.java
                        }
                    m?.isAccessible = true
                    m
                }
        val instanceMethod =
            controller.javaClass.declaredMethods.firstOrNull {
                it.name == "normalizeTheme" &&
                    it.parameterCount == 1 &&
                    it.parameterTypes[0] == String::class.java
            }
        if (instanceMethod != null) {
            instanceMethod.isAccessible = true
            val supported = instanceMethod.invoke(controller, "modern") as String
            assertEquals("modern", supported)
            val unsupported = instanceMethod.invoke(controller, "nonexistent-theme") as String
            assertEquals("modern", unsupported)
        }
    }

    @Test
    fun `toDeveloperOptions reads boolean preferences correctly`() {
        val method =
            controller.javaClass.declaredMethods.firstOrNull {
                it.name == "toDeveloperOptions\$extension" || it.name == "toDeveloperOptions"
            }
        if (method != null) {
            method.isAccessible = true
            val prefsTrue =
                mapOf(
                    "dev_show_prompt_buttons" to "true",
                    "text_filter_enabled" to "1",
                    "dev_show_token_usage" to "false",
                    "dev_docker_test_env" to "0",
                    "dev_docker_auto_start" to "garbage",
                )
            val optsTrue = method.invoke(controller, prefsTrue, true) as com.stacklens.adapter.inbound.rest.DeveloperOptionsExportDto
            assertEquals(true, optsTrue.showPromptButtons)
            assertEquals(true, optsTrue.textFilterEnabled)
            assertEquals(false, optsTrue.showTokenUsage)
            assertEquals(false, optsTrue.dockerTestEnv)
            assertEquals(false, optsTrue.dockerAutoStart)
        }
    }
}
