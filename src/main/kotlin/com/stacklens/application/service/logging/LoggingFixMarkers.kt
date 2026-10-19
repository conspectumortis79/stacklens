package com.stacklens.application.service.logging

import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.pipeline.LoggingInputHeuristic

@ApplicationService
class LoggingFixMarkers(
    private val i18n: I18nPort,
) {
    fun skipMarker(language: String): String = render(language, SKIP_MARKER_KEY, LoggingInputHeuristic.MIN_NON_BLANK_LINES)

    fun emptyResultMarker(
        stage: String,
        language: String,
    ): String = render(language, EMPTY_RESULT_MARKER_KEY, stageLabel(stage, language))

    fun stageSkippedNoDiagnosisMarker(
        stage: String,
        language: String,
    ): String = render(language, STAGE_SKIPPED_NO_DIAGNOSIS_MARKER_KEY, stageLabel(stage, language))

    fun stageSkippedNoRepositoryMarker(
        stage: String,
        language: String,
    ): String =
        render(
            language,
            STAGE_SKIPPED_NO_REPO_MARKER_KEY,
            stageLabel(stage, language),
            render(language, NO_REPO_REASON_KEY_PREFIX + stage),
        )

    fun categoriesAll(language: String): String = render(language, CATEGORIES_ALL_KEY)

    fun skipSignatures(): List<String> =
        CATALOG_LANGUAGES.flatMap { language ->
            SKIP_SIGNATURE_KEYS.map { key -> i18n.tFor(language, key) }
        }

    fun isSkipMarker(text: String): Boolean = LoggingInputHeuristic.isStageSkipMarker(text, skipSignatures())

    private fun stageLabel(
        stage: String,
        language: String,
    ): String = render(language, STAGE_NAME_KEY_PREFIX + stage)

    private fun render(
        language: String,
        key: String,
        vararg args: Any,
    ): String = i18n.tFor(language.lowercase(), key, *args)

    companion object {
        const val SKIP_MARKER_KEY: String = "logging_fix.be_skip_marker"
        const val EMPTY_RESULT_MARKER_KEY: String = "logging_fix.be_empty_result_marker"
        const val STAGE_SKIPPED_NO_DIAGNOSIS_MARKER_KEY: String = "logging_fix.be_stage_skipped_no_diagnosis_marker"
        const val STAGE_SKIPPED_NO_REPO_MARKER_KEY: String = "logging_fix.be_stage_skipped_no_repo_marker"
        const val CATEGORIES_ALL_KEY: String = "logging_fix.be_categories_all"

        val SKIP_SIGNATURE_KEYS: List<String> =
            listOf(
                "logging_fix.be_skip_signature_diagnosis_skipped",
                "logging_fix.be_skip_signature_input_too_thin",
                "logging_fix.be_skip_signature_diagnosis_not_possible",
                "logging_fix.be_skip_signature_no_repository",
                "logging_fix.be_skip_signature_empty_result",
            )

        private const val STAGE_NAME_KEY_PREFIX: String = "logging_fix.be_stage_name_"
        private const val NO_REPO_REASON_KEY_PREFIX: String = "logging_fix.be_no_repo_reason_"
        private val CATALOG_LANGUAGES: List<String> = listOf("de", "en")
    }
}
