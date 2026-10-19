
package com.stacklens.application.service

import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.TranslationCatalogPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.failure.StackLensFailure
import com.stacklens.domain.failure.StackLensFailureException
import java.util.concurrent.ConcurrentHashMap

@ApplicationService
class I18nService(
    private val translationCatalog: TranslationCatalogPort,
    private val aiConfigRepo: AiConfigPort,
) : I18nUseCase,
    I18nPort {
    override fun resolveLanguage(): String = aiConfigRepo.findAiConfig()?.language?.takeIf { it.isNotBlank() } ?: "de"

    private val cache = ConcurrentHashMap<String, Map<String, String>>()

    override fun t(
        key: String,
        vararg args: Any,
    ): String = tFor(resolveLanguage(), key, *args)

    override fun tFor(
        lang: String,
        key: String,
        vararg args: Any,
    ): String {
        val messages = cache.computeIfAbsent(lang) { translationCatalog.load(it) }
        val template = messages[key] ?: cache.computeIfAbsent("de") { translationCatalog.load(it) }[key] ?: key
        if (args.isEmpty() || !template.contains('{')) return template
        return PLACEHOLDER_REGEX.replace(template) { m ->
            val idx = m.groupValues[1].toInt()
            if (idx < args.size) args[idx].toString() else m.value
        }
    }

    private companion object {
        private val PLACEHOLDER_REGEX = Regex("""\{(\d+)\}""")
    }

    override fun translateError(e: Exception): String =
        (e as? StackLensFailureException)?.failure?.let { translated(it) }
            ?: e.message
            ?: e.toString()

    private fun translated(failure: StackLensFailure): String =
        when (failure) {
            is StackLensFailure.CopilotQuota -> t("common.be_copilot_quota")
            StackLensFailure.CopilotTokenMissing -> t("common.be_copilot_no_token")
            StackLensFailure.CopilotNotInstalled -> t("common.be_copilot_not_installed")
            StackLensFailure.CopilotEmptyResponse -> t("common.be_copilot_empty_response")
            StackLensFailure.GhCliMissing -> t("common.be_gh_not_installed")
            StackLensFailure.CopilotInstallFailed -> t("common.be_copilot_install_failed")
            is StackLensFailure.CopilotTimeout ->
                t(
                    "common.be_copilot_timeout",
                    failure.timeoutSeconds,
                    failure.promptChars,
                )
            is StackLensFailure.CopilotNoOutput -> t("common.be_copilot_no_output", failure.exitCode)
            is StackLensFailure.AiCreditsExhausted ->
                t(
                    "common.be_ai_credits_exhausted",
                    failure.attemptedCredentials,
                    failure.detail,
                )
            is StackLensFailure.AiCredentialRejected ->
                t(
                    "common.be_ai_credential_rejected",
                    failure.attemptedCredentials,
                    failure.status,
                    failure.detail,
                )
            is StackLensFailure.AiOutputTruncated ->
                t(
                    "common.be_ai_output_truncated",
                    failure.outputTokens,
                    failure.thinkingTokens,
                    failure.textChars,
                )
            is StackLensFailure.CopilotExitError -> {
                val prefix = t("common.be_copilot_exit_error", failure.exitCode)
                val detail = failure.detail.trim()
                if (detail.isNotBlank()) "$prefix $detail" else prefix
            }
            is StackLensFailure.RepositoryDirectoryMissing -> t("config.be_repo_dir_not_exists", failure.path)
            is StackLensFailure.MergeRequestConnectionRefused -> t("mr_analysis.be_err_conn_refused", failure.host)
            is StackLensFailure.JiraConnectionRefused -> t("review_analysis.be_err_jira_conn_refused", failure.host)
        }
}
