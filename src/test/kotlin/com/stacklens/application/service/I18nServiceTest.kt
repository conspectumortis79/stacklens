package com.stacklens.application.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.out.filesystem.ClasspathTranslationCatalogAdapter
import com.stacklens.adapter.out.shared.JacksonJsonCodecAdapter
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.TranslationCatalogPort
import com.stacklens.domain.failure.StackLensFailure
import com.stacklens.domain.failure.StackLensFailureException
import com.stacklens.domain.model.AiConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.core.io.Resource
import org.springframework.core.io.ResourceLoader

@ExtendWith(MockitoExtension::class)
class I18nServiceTest {
    private val objectMapper = ObjectMapper().apply { findAndRegisterModules() }
    private val jsonCodec = JacksonJsonCodecAdapter(objectMapper)
    private val aiConfigRepo: AiConfigPort = mock()
    private lateinit var service: I18nService

    @BeforeEach
    fun setUp() {
        service = I18nService(catalogFor(DefaultResourceLoader()), aiConfigRepo)
    }

    @Test
    fun `resolveLanguage returns language from AiConfig when set`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(language = "en"))
        assertEquals("en", service.resolveLanguage())
    }

    @Test
    fun `resolveLanguage returns de when AiConfig language is blank`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(language = ""))
        assertEquals("de", service.resolveLanguage())
    }

    @Test
    fun `resolveLanguage returns de when AiConfig is null`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        assertEquals("de", service.resolveLanguage())
    }

    @Test
    fun `t returns key as fallback when translation file is missing`() {
        val result = service.tFor("zz", "some.missing.key")
        assertEquals("some.missing.key", result)
    }

    @Test
    fun `t returns german translation for known key`() {
        val result = service.tFor("de", "nav.docker_test")
        assertTrue(result.isNotBlank())
        assertTrue(result != "nav.docker_test")
    }

    @Test
    fun `t returns english translation for known key`() {
        val result = service.tFor("en", "nav.docker_test")
        assertTrue(result.isNotBlank())
        assertTrue(result != "nav.docker_test")
    }

    @Test
    fun `t substitutes indexed placeholders positionally`() {
        val result = service.tFor("de", "jwt_analysis.be_time_days_hours", 2, 3)
        assertTrue(result.contains("2"))
        assertTrue(result.contains("3"))
    }

    @Test
    fun `t falls back to de translation when lang file is missing for a known key`() {
        val result = service.tFor("xx", "nav.docker_test")
        assertTrue(result != "nav.docker_test")
    }

    @Test
    fun `t uses current language via resolveLanguage overload`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(language = "en"))
        val result = service.t("nav.docker_test")
        assertTrue(result.isNotBlank())
        assertTrue(result != "nav.docker_test")
    }

    @Test
    fun `docker_test status_idle is translated in de`() {
        val result = service.tFor("de", "docker_test.status_idle")
        assertTrue(result.isNotBlank(), "de translation for docker_test.status_idle must not be blank")
        assertTrue(result != "docker_test.status_idle", "de translation for docker_test.status_idle must not fall through to key")
    }

    @Test
    fun `docker_test status_idle is translated in en`() {
        val result = service.tFor("en", "docker_test.status_idle")
        assertTrue(result.isNotBlank(), "en translation for docker_test.status_idle must not be blank")
        assertTrue(result != "docker_test.status_idle", "en translation for docker_test.status_idle must not fall through to key")
    }

    @Test
    fun `docker_test status_running is translated in de`() {
        val result = service.tFor("de", "docker_test.status_running")
        assertTrue(result.isNotBlank(), "de translation for docker_test.status_running must not be blank")
        assertTrue(result != "docker_test.status_running", "de translation for docker_test.status_running must not fall through to key")
    }

    @Test
    fun `docker_test status_running is translated in en`() {
        val result = service.tFor("en", "docker_test.status_running")
        assertTrue(result.isNotBlank(), "en translation for docker_test.status_running must not be blank")
        assertTrue(result != "docker_test.status_running", "en translation for docker_test.status_running must not fall through to key")
    }

    @Test
    fun `docker_test status_done is translated in de`() {
        val result = service.tFor("de", "docker_test.status_done")
        assertTrue(result.isNotBlank(), "de translation for docker_test.status_done must not be blank")
        assertTrue(result != "docker_test.status_done", "de translation for docker_test.status_done must not fall through to key")
    }

    @Test
    fun `docker_test status_done is translated in en`() {
        val result = service.tFor("en", "docker_test.status_done")
        assertTrue(result.isNotBlank(), "en translation for docker_test.status_done must not be blank")
        assertTrue(result != "docker_test.status_done", "en translation for docker_test.status_done must not fall through to key")
    }

    @Test
    fun `docker_test status_failed is translated in de`() {
        val result = service.tFor("de", "docker_test.status_failed")
        assertTrue(result.isNotBlank(), "de translation for docker_test.status_failed must not be blank")
        assertTrue(result != "docker_test.status_failed", "de translation for docker_test.status_failed must not fall through to key")
    }

    @Test
    fun `docker_test status_failed is translated in en`() {
        val result = service.tFor("en", "docker_test.status_failed")
        assertTrue(result.isNotBlank(), "en translation for docker_test.status_failed must not be blank")
        assertTrue(result != "docker_test.status_failed", "en translation for docker_test.status_failed must not fall through to key")
    }

    @Test
    fun `docker_test page chrome is translated in both languages`() {
        listOf(
            "docker_test.page_title",
            "docker_test.page_subtitle",
            "docker_test.btn_start",
            "docker_test.btn_reset",
            "docker_test.output_title",
            "docker_test.btn_save_rebuild",
            "docker_test.data_files_title",
            "docker_test.data_files_loading",
            "docker_test.data_files_empty",
            "docker_test.compose_not_found",
            "docker_test.docker_not_available",
            "docker_test.btn_restart_docker",
            "docker_test.restarting",
            "docker_test.restart_success",
            "docker_test.restart_failed",
            "docker_test.editor_rebuilding",
            "docker_test.editor_save_error",
            "docker_test.confirm_reset_title",
            "docker_test.confirm_reset_message",
            "docker_test.confirm_reset_ok",
            "docker_test.confirm_rebuild_title",
            "docker_test.confirm_rebuild_message",
            "docker_test.confirm_rebuild_ok",
        ).forEach { key ->
            for (lang in listOf("de", "en")) {
                val value = service.tFor(lang, key)
                assertTrue(value.isNotBlank(), "$lang translation for $key must not be blank")
                assertTrue(value != key, "$lang translation for $key must not fall through to key")
            }
        }
    }

    @Test
    fun `docker_test status labels differ between de and en`() {
        val deValues =
            listOf("idle", "running", "done", "failed")
                .map { service.tFor("de", "docker_test.status_$it") }
        val enValues =
            listOf("idle", "running", "done", "failed")
                .map { service.tFor("en", "docker_test.status_$it") }
        assertEquals(4, deValues.toSet().size, "de status labels must all be unique – got $deValues")
        assertEquals(4, enValues.toSet().size, "en status labels must all be unique – got $enValues")
        assertTrue(deValues != enValues, "de and en status labels should differ – both were $deValues")
    }

    @Test
    fun `t is cached after first load`() {
        val first = service.tFor("de", "nav.docker_test")
        val second = service.tFor("de", "nav.docker_test")
        assertEquals(first, second)
    }

    @Test
    fun `t with placeholders uses a shared regex instance`() {
        val customService =
            I18nService(catalogFor(jsonResourceLoader("en", """{"a":{"b":"x={0} y={1} z={2}","c":"no placeholders here"}}""")), aiConfigRepo)
        assertEquals("no placeholders here", customService.tFor("en", "a.c"))
        assertEquals("x=A y={1} z={2}", customService.tFor("en", "a.b", "A"))
        assertEquals("x=A y=B z=C", customService.tFor("en", "a.b", "A", "B", "C"))
        assertEquals("x=A y=B z={2}", customService.tFor("en", "a.b", "A", "B"))
    }

    @Test
    fun `t with placeholders preserves stability under repeated calls`() {
        val customService =
            I18nService(catalogFor(jsonResourceLoader("en", """{"a":{"b":"prefix {0} suffix"}}""")), aiConfigRepo)
        val first = customService.tFor("en", "a.b", "X")
        repeat(1_000) {
            assertEquals(first, customService.tFor("en", "a.b", "X"))
        }
    }

    private val pipelineKeys =
        listOf(
            "pipeline.status_completed",
            "pipeline.status_aborted",
            "pipeline.status_error",
            "pipeline.running_status",
            "pipeline.ai_prompt_btn",
            "pipeline.tab_summary",
            "pipeline.tab_findings",
            "pipeline.tab_files",
            "pipeline.tab_optimizations",
            "pipeline.select_hint",
            "pipeline.analyzing",
            "pipeline.analyzing_hint",
            "pipeline.aborted",
            "pipeline.aborted_hint",
            "pipeline.arch_run_hint",
            "pipeline.mermaid_unavailable",
            "pipeline.save_mermaid",
            "pipeline.user_prompt_label",
            "pipeline.user_prompt_hint",
            "pipeline.user_prompt_ph",
            "pipeline.no_findings_js",
            "pipeline.no_descriptions_js",
            "pipeline.no_optimizations_js",
            "pipeline.no_analysis",
            "pipeline.no_findings",
            "pipeline.no_descriptions",
            "pipeline.no_optimizations",
            "pipeline.repo_add_title",
            "pipeline.repo_path_label",
            "pipeline.dir_select_title",
            "pipeline.parent_dir",
            "pipeline.open",
            "pipeline.selected",
            "pipeline.select_btn",
            "pipeline.history_title",
            "pipeline.history_none_entries",
            "pipeline.history_one",
            "pipeline.history_many",
            "pipeline.history_view",
            "pipeline.col_repo",
            "pipeline.col_status",
            "pipeline.col_created",
            "pipeline.btn_start",
            "pipeline.delete_confirm",
            "pipeline.key_label",
            "pipeline.value_label",
            "pipeline.meaning_label",
            "pipeline.no_gitlab_config",
            "pipeline.mermaid_not_loaded",
            "pipeline.diagram_render_error",
            "pipeline.be_unknown_error",
        )

    @Test
    fun `pipeline page chrome is translated in both languages`() {
        for (key in pipelineKeys) {
            for (lang in listOf("de", "en")) {
                val value = service.tFor(lang, key)
                assertTrue(
                    value.isNotBlank(),
                    "$lang translation for $key must not be blank",
                )
                assertTrue(
                    value != key,
                    "$lang translation for $key must not fall through to key – got '$value'",
                )
            }
        }
    }

    @Test
    fun `pipeline status_completed is translated distinctly in de and en`() {
        val de = service.tFor("de", "pipeline.status_completed")
        val en = service.tFor("en", "pipeline.status_completed")
        assertTrue(de.isNotBlank() && de != "pipeline.status_completed")
        assertTrue(en.isNotBlank() && en != "pipeline.status_completed")
        assertTrue(
            de != en,
            "pipeline.status_completed must differ between de ('$de') and en ('$en')",
        )
    }

    @Test
    fun `pipeline btn_start is translated in both languages`() {
        val de = service.tFor("de", "pipeline.btn_start")
        val en = service.tFor("en", "pipeline.btn_start")
        assertTrue(de.isNotBlank() && de != "pipeline.btn_start")
        assertTrue(en.isNotBlank() && en != "pipeline.btn_start")
    }

    @Test
    fun `pipeline select_hint is translated in both languages`() {
        val de = service.tFor("de", "pipeline.select_hint")
        val en = service.tFor("en", "pipeline.select_hint")
        assertTrue(de.isNotBlank() && de != "pipeline.select_hint")
        assertTrue(en.isNotBlank() && en != "pipeline.select_hint")
    }

    @Test
    fun `pipeline backend error keys are translated in both languages`() {
        for (key in listOf(
            "pipeline.be_aborted_restart",
            "pipeline.be_analysis_not_found",
            "pipeline.be_cancelled",
            "pipeline.be_clone_error",
            "pipeline.be_dir_exists",
            "pipeline.be_dir_not_found_save",
            "pipeline.be_finn_no_output",
            "pipeline.be_gitlab_config_not_found",
            "pipeline.be_invalid_clone_url",
            "pipeline.be_no_github_token",
            "pipeline.be_no_repo_linked",
            "pipeline.be_raw_text_empty",
            "pipeline.be_raw_text_too_long",
            "pipeline.be_repo_config_not_found",
            "pipeline.be_save_failed",
            "pipeline.be_target_dir_missing",
            "pipeline.be_ticket_fetch_failed",
            "pipeline.be_ticket_key_empty",
            "pipeline.be_unknown_error",
        )) {
            for (lang in listOf("de", "en")) {
                val value = service.tFor(lang, key)
                assertTrue(value.isNotBlank(), "$lang translation for $key must not be blank")
                assertTrue(value != key, "$lang translation for $key must not fall through to key")
            }
        }
    }

    @Test
    fun `pipeline status labels are unique per language and differ across languages`() {
        val deValues =
            listOf(
                "pipeline.status_completed",
                "pipeline.status_aborted",
                "pipeline.status_error",
                "pipeline.running_status",
            ).map { service.tFor("de", it) }
        val enValues =
            listOf(
                "pipeline.status_completed",
                "pipeline.status_aborted",
                "pipeline.status_error",
                "pipeline.running_status",
            ).map { service.tFor("en", it) }
        assertEquals(4, deValues.toSet().size, "de status labels must all be unique – got $deValues")
        assertEquals(4, enValues.toSet().size, "en status labels must all be unique – got $enValues")
        assertTrue(deValues != enValues, "de and en status labels should differ – both were $deValues")
    }

    @Test
    fun `translateError maps COPILOT_QUOTA marker to i18n key`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(language = "de"))
        val ex = StackLensFailureException(StackLensFailure.CopilotQuota("quota exceeded"))
        val msg = service.translateError(ex)
        assertTrue(msg != "[COPILOT_QUOTA] quota exceeded")
        assertTrue(msg.isNotBlank())
    }

    @Test
    fun `translateError returns raw message for non-quota exceptions`() {
        val ex = RuntimeException("some random error")
        val msg = service.translateError(ex)
        assertEquals("some random error", msg)
    }

    @Test
    fun `translateError returns fallback string when exception message is null`() {
        val ex = object : Exception(null as String?) {}
        val msg = service.translateError(ex)
        assertTrue(msg.isNotBlank())
    }

    @Test
    fun `translateError maps quota marker even without suffix`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(language = "de"))
        val msg = service.translateError(StackLensFailureException(StackLensFailure.CopilotQuota("")))
        assertFalse(msg.startsWith("[COPILOT_QUOTA]"))
        assertTrue(msg.isNotBlank())
    }

    @Test
    fun `translateError maps MR_CONN_REFUSED marker to i18n key with host`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(language = "de"))
        val ex = StackLensFailureException(StackLensFailure.MergeRequestConnectionRefused("gitlab.example.com"))
        val msg = service.translateError(ex)
        assertTrue(msg != "[MR_CONN_REFUSED:gitlab.example.com]")
        assertTrue(msg.isNotBlank())
    }

    @Test
    fun `translateError maps the exhausted credit marker to the i18n key with its counters`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(language = "de"))
        val ex = StackLensFailureException(StackLensFailure.AiCreditsExhausted(2, "credit balance is too low"))

        val msg = service.translateError(ex)

        assertFalse(msg.startsWith("[AI_CREDITS_EXHAUSTED"))
        assertTrue(msg.contains("2"), msg)
        assertTrue(msg.contains("credit balance is too low"), msg)
    }

    @Test
    fun `translateError maps the rejected credential marker with status and detail`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(language = "en"))
        val ex = StackLensFailureException(StackLensFailure.AiCredentialRejected(403, 3, "permission_error"))

        val msg = service.translateError(ex)

        assertFalse(msg.startsWith("[AI_CREDENTIAL_REJECTED"))
        assertTrue(msg.contains("HTTP 403"), msg)
        assertTrue(msg.contains("permission_error"), msg)
    }

    @Test
    fun `translateError maps the truncated output marker to the i18n key with its counters`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(language = "de"))
        val ex = StackLensFailureException(StackLensFailure.AiOutputTruncated(8192, 8192, 0))

        val msg = service.translateError(ex)

        assertFalse(msg.startsWith("[AI_OUTPUT_TRUNCATED"))
        assertTrue(msg.contains("8192"), msg)
        assertTrue(service.tFor("en", "common.be_ai_output_truncated", 8192, 8192, 0).contains("truncated"))
    }

    @Test
    fun `t loads nested messages from custom resource and ignores non string values`() {
        val customService =
            I18nService(
                catalogFor(
                    jsonResourceLoader(
                        "en",
                        """
                        {
                          "outer": {
                            "inner": "value",
                            "number": 7,
                            "deep": { "message": "Hello {1} then {0} then {0}" }
                          }
                        }
                        """.trimIndent(),
                    ),
                ),
                aiConfigRepo,
            )

        assertEquals("value", customService.tFor("en", "outer.inner"))
        assertEquals("outer.number", customService.tFor("en", "outer.number"))
        assertEquals("Hello B then A then A", customService.tFor("en", "outer.deep.message", "A", "B"))
    }

    @Test
    fun `t falls back to key when language json is malformed`() {
        val customService =
            I18nService(catalogFor(jsonResourceLoader("en", "{ malformed json")), aiConfigRepo)

        assertEquals("broken.key", customService.tFor("en", "broken.key"))
    }

    @Test
    fun `translateError maps COPILOT_NO_TOKEN marker`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(language = "de"))
        val msg = service.translateError(StackLensFailureException(StackLensFailure.CopilotTokenMissing))
        assertFalse(msg.startsWith("[COPILOT_NO_TOKEN]"))
        assertTrue(msg.isNotBlank())
    }

    @Test
    fun `translateError maps COPILOT_NOT_INSTALLED marker`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(language = "de"))
        val msg = service.translateError(StackLensFailureException(StackLensFailure.CopilotNotInstalled))
        assertFalse(msg.startsWith("[COPILOT_NOT_INSTALLED]"))
        assertTrue(msg.isNotBlank())
    }

    @Test
    fun `translateError maps COPILOT_EMPTY_RESPONSE marker`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(language = "de"))
        val msg = service.translateError(StackLensFailureException(StackLensFailure.CopilotEmptyResponse))
        assertFalse(msg.startsWith("[COPILOT_EMPTY_RESPONSE]"))
        assertTrue(msg.isNotBlank())
    }

    @Test
    fun `translateError maps GH_NOT_INSTALLED marker`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(language = "de"))
        val msg = service.translateError(StackLensFailureException(StackLensFailure.GhCliMissing))
        assertFalse(msg.startsWith("[GH_NOT_INSTALLED]"))
        assertTrue(msg.isNotBlank())
    }

    @Test
    fun `translateError maps COPILOT_INSTALL_FAILED marker`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(language = "de"))
        val msg = service.translateError(StackLensFailureException(StackLensFailure.CopilotInstallFailed))
        assertFalse(msg.startsWith("[COPILOT_INSTALL_FAILED]"))
        assertTrue(msg.isNotBlank())
    }

    @Test
    fun `translateError maps COPILOT_TIMEOUT marker with two parts`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(language = "de"))
        val msg = service.translateError(StackLensFailureException(StackLensFailure.CopilotTimeout(timeoutSeconds = 30L, promptChars = 1234)))
        assertFalse(msg.startsWith("[COPILOT_TIMEOUT"))
        assertTrue(msg.contains("30"))
        assertTrue(msg.contains("1234"))
    }

    @Test
    fun `translateError leaves untyped marker messages untouched`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(language = "de"))
        val msg = service.translateError(RuntimeException("[COPILOT_TIMEOUT:60]"))
        assertEquals("[COPILOT_TIMEOUT:60]", msg)
    }

    @Test
    fun `failure exceptions keep the wire message for logs and adapters`() {
        assertEquals("[COPILOT_TIMEOUT:30:1234]", StackLensFailureException(StackLensFailure.CopilotTimeout(30L, 1234)).message)
        assertEquals("[COPILOT_NO_TOKEN]", StackLensFailureException(StackLensFailure.CopilotTokenMissing).message)
        assertEquals("[JIRA_CONN_REFUSED:host:1]", StackLensFailureException(StackLensFailure.JiraConnectionRefused("host:1")).message)
    }

    @Test
    fun `translateError maps COPILOT_NO_OUTPUT marker`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(language = "de"))
        val msg = service.translateError(StackLensFailureException(StackLensFailure.CopilotNoOutput(42)))
        assertFalse(msg.startsWith("[COPILOT_NO_OUTPUT"))
        assertTrue(msg.isNotBlank())
    }

    @Test
    fun `translateError maps COPILOT_EXIT_ERROR marker with blank detail`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(language = "de"))
        val msg = service.translateError(StackLensFailureException(StackLensFailure.CopilotExitError(exitCode = 1, detail = "")))
        assertFalse(msg.startsWith("[COPILOT_EXIT_ERROR"))
        assertTrue(msg.isNotBlank())
    }

    @Test
    fun `translateError maps COPILOT_EXIT_ERROR marker with non-blank detail`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(language = "de"))
        val msg = service.translateError(StackLensFailureException(StackLensFailure.CopilotExitError(exitCode = 2, detail = "some detail here")))
        assertFalse(msg.startsWith("[COPILOT_EXIT_ERROR"))
        assertTrue(msg.isNotBlank())
        assertTrue(msg.contains("some detail here"))
    }

    @Test
    fun `translateError maps REPO_DIR_NOT_FOUND marker`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(language = "de"))
        val msg = service.translateError(StackLensFailureException(StackLensFailure.RepositoryDirectoryMissing("/home/user/repo")))
        assertFalse(msg.startsWith("[REPO_DIR_NOT_FOUND"))
        assertTrue(msg.isNotBlank())
    }

    @Test
    fun `t substitutes out-of-bounds placeholder index with original placeholder`() {
        val customService =
            I18nService(catalogFor(jsonResourceLoader("en", """{"test": {"key": "Hello {0} and {2}"}}""")), aiConfigRepo)
        val result = customService.tFor("en", "test.key", "World")
        assertTrue(result.contains("World"))
        assertTrue(result.contains("{2}"))
    }

    @Test
    fun `translateError renders quota without the raw detail`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(language = "de"))
        val msg = service.translateError(StackLensFailureException(StackLensFailure.CopilotQuota("stdout stdout stdout")))
        assertFalse(msg.contains("stdout"))
        assertTrue(msg.isNotBlank())
    }

    @Test
    fun `translateError maps MR_CONN_REFUSED marker`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(language = "de"))
        val msg = service.translateError(StackLensFailureException(StackLensFailure.MergeRequestConnectionRefused("localhost:8080")))
        assertFalse(msg.startsWith("[MR_CONN_REFUSED"))
        assertTrue(msg.isNotBlank())
    }

    @Test
    fun `translateError maps JIRA_CONN_REFUSED marker with host`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(language = "de"))
        val msg = service.translateError(StackLensFailureException(StackLensFailure.JiraConnectionRefused("localhost:8081")))
        assertFalse(msg.startsWith("[JIRA_CONN_REFUSED"))
        assertTrue(msg.contains("localhost:8081"))
    }

    @Test
    fun `translateError maps JIRA_CONN_REFUSED marker in English`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(language = "en"))
        val msg = service.translateError(StackLensFailureException(StackLensFailure.JiraConnectionRefused("jira.example.com")))
        assertFalse(msg.startsWith("[JIRA_CONN_REFUSED"))
        assertTrue(msg.contains("jira.example.com"))
    }

    private fun jsonResourceLoader(
        lang: String,
        content: String,
    ): ResourceLoader =
        object : ResourceLoader {
            override fun getResource(location: String): Resource =
                if (location == "classpath:static/i18n/$lang.json") {
                    ByteArrayResource(content.toByteArray())
                } else {
                    object : ByteArrayResource(ByteArray(0)) {
                        override fun exists(): Boolean = false
                    }
                }

            override fun getClassLoader(): ClassLoader = javaClass.classLoader
        }

    private fun catalogFor(loader: ResourceLoader): TranslationCatalogPort = ClasspathTranslationCatalogAdapter(loader, jsonCodec)
}
