package com.stacklens.adapter.out.ai

internal object CopilotOutputSanitizer {
    private val GH_TOKEN_REGEX = Regex("gh[pousr]_[A-Za-z0-9]{20,}")

    private val BEARER_REGEX = Regex("(?i)(authorization\\s*:\\s*bearer\\s+)\\S+")

    private val ENV_TOKEN_REGEX = Regex("(GH_TOKEN|COPILOT_GITHUB_TOKEN)\\s*=\\s*\\S+")

    private val SYSTEM_NOTIFICATION_REGEX =
        Regex("<system_notification>[\\s\\S]*?</system_notification>", RegexOption.IGNORE_CASE)

    private val BLANK_LINES_REGEX = Regex("\n{3,}")

    fun isQuotaError(output: String): Boolean =
        output.contains("402") ||
            output.contains("Payment Required", ignoreCase = true) ||
            output.contains("quota exceeded", ignoreCase = true) ||
            output.contains("rate limit", ignoreCase = true) ||
            output.contains("Spending limit", ignoreCase = true)

    fun isNotInstalledError(output: String): Boolean =
        output.contains("Cannot find GitHub Copilot CLI", ignoreCase = true) ||
            output.contains("Install GitHub Copilot CLI", ignoreCase = true)

    fun scrubSensitiveData(s: String): String =
        s
            .replace(GH_TOKEN_REGEX, "[REDACTED_PAT]")
            .replace(BEARER_REGEX, "$1[REDACTED]")
            .replace(ENV_TOKEN_REGEX, "$1=[REDACTED]")

    fun stripSystemNotifications(text: String): String =
        text
            .replace(SYSTEM_NOTIFICATION_REGEX, "")
            .replace(BLANK_LINES_REGEX, "\n\n")
            .trim()
}
