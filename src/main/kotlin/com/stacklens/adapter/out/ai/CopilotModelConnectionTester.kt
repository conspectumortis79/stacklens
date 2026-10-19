package com.stacklens.adapter.out.ai

import com.stacklens.adapter.out.shared.connectionFailureKindFromMessage
import com.stacklens.application.port.model.ConnectionFailureKind
import com.stacklens.application.port.out.ai.AiConnectionTestPort.TestResult
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.ai.ProviderSpec
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.domain.enums.AiProvider
import com.stacklens.domain.failure.StackLensFailure
import com.stacklens.domain.failure.StackLensFailureException
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

@Component
class CopilotModelConnectionTester(
    private val copilotPort: CopilotPort,
    private val i18n: I18nPort,
) : AiProviderTester<ProviderSpec.CopilotModel> {
    override val specType: KClass<ProviderSpec.CopilotModel> = ProviderSpec.CopilotModel::class

    override fun test(spec: ProviderSpec.CopilotModel): TestResult {
        val model = spec.model
        if (model.isNullOrBlank()) {
            return TestResult.Failure(
                ConnectionFailureKind.OTHER,
                mapOf("success" to false, "error" to i18n.t("config.be_no_model_selected")),
            )
        }
        if (spec.savedProvider != AiProvider.COPILOT.id) {
            return TestResult.Failure(ConnectionFailureKind.OTHER, mapOf("success" to false, "error" to i18n.t("config.save_first")))
        }
        if (copilotPort.resolveToken() == null) {
            return TestResult.Failure(
                ConnectionFailureKind.AUTHENTICATION,
                mapOf("success" to false, "error" to i18n.t("config.be_no_token")),
            )
        }

        return runCatching {
            copilotPort.execute(model, "Reply with exactly one word: OK", 30L)
            TestResult.Success(mapOf("success" to true, "model" to model))
        }.getOrElse { e ->
            val msg = e.message ?: ""
            val quotaExceeded =
                (e as? StackLensFailureException)?.failure is StackLensFailure.CopilotQuota
            val localised =
                when {
                    quotaExceeded -> i18n.t("common.be_copilot_quota")
                    msg.contains("not found", ignoreCase = true) ||
                        msg.contains("not supported", ignoreCase = true) ||
                        msg.contains("not available", ignoreCase = true) ||
                        msg.contains("unknown model", ignoreCase = true) ||
                        msg.contains("invalid model", ignoreCase = true) ||
                        msg.contains("404") -> i18n.t("config.be_model_not_available", model)

                    msg.contains("401") ||
                        msg.contains("403") ||
                        msg.contains("unauthorized", ignoreCase = true) ||
                        msg.contains("forbidden", ignoreCase = true) -> i18n.t("config.be_model_unauthorized", model)

                    msg.contains("Timeout", ignoreCase = true) -> i18n.t("config.be_model_timeout", model)
                    msg.contains("nicht installiert", ignoreCase = true) ||
                        msg.contains("not installed", ignoreCase = true) ||
                        msg.contains("Cannot find GitHub Copilot CLI", ignoreCase = true) -> i18n.t("config.be_copilot_not_installed")

                    else -> i18n.t("config.be_model_test_failed", model, msg.take(200))
                }
            TestResult.Failure(
                connectionFailureKindFromMessage(msg),
                mapOf("success" to false, "error" to localised, "model" to model),
            )
        }
    }
}
