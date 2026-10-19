package com.stacklens.domain.failure

sealed class StackLensFailure {
    abstract val wireMessage: String

    data class CopilotQuota(
        val raw: String,
    ) : StackLensFailure() {
        override val wireMessage: String
            get() = "[COPILOT_QUOTA] $raw".take(QUOTA_RENDER_LIMIT)
    }

    data object CopilotTokenMissing : StackLensFailure() {
        override val wireMessage: String
            get() = "[COPILOT_NO_TOKEN]"
    }

    data object CopilotNotInstalled : StackLensFailure() {
        override val wireMessage: String
            get() = "[COPILOT_NOT_INSTALLED]"
    }

    data object CopilotEmptyResponse : StackLensFailure() {
        override val wireMessage: String
            get() = "[COPILOT_EMPTY_RESPONSE]"
    }

    data object GhCliMissing : StackLensFailure() {
        override val wireMessage: String
            get() = "[GH_NOT_INSTALLED]"
    }

    data object CopilotInstallFailed : StackLensFailure() {
        override val wireMessage: String
            get() = "[COPILOT_INSTALL_FAILED]"
    }

    data class CopilotTimeout(
        val timeoutSeconds: Long,
        val promptChars: Int,
    ) : StackLensFailure() {
        override val wireMessage: String
            get() = "[COPILOT_TIMEOUT:$timeoutSeconds:$promptChars]"
    }

    data class CopilotNoOutput(
        val exitCode: Int,
    ) : StackLensFailure() {
        override val wireMessage: String
            get() = "[COPILOT_NO_OUTPUT:$exitCode]"
    }

    data class CopilotExitError(
        val exitCode: Int,
        val detail: String,
    ) : StackLensFailure() {
        override val wireMessage: String
            get() = "[COPILOT_EXIT_ERROR:$exitCode] $detail"
    }

    data class AiOutputTruncated(
        val outputTokens: Int,
        val thinkingTokens: Int,
        val textChars: Int,
    ) : StackLensFailure() {
        override val wireMessage: String
            get() = "[AI_OUTPUT_TRUNCATED:$outputTokens:$thinkingTokens:$textChars]"
    }

    data class AiCreditsExhausted(
        val attemptedCredentials: Int,
        val detail: String,
    ) : StackLensFailure() {
        override val wireMessage: String
            get() = "[AI_CREDITS_EXHAUSTED:$attemptedCredentials] $detail"
    }

    data class AiCredentialRejected(
        val status: Int,
        val attemptedCredentials: Int,
        val detail: String,
    ) : StackLensFailure() {
        override val wireMessage: String
            get() = "[AI_CREDENTIAL_REJECTED:$status:$attemptedCredentials] $detail"
    }

    data class RepositoryDirectoryMissing(
        val path: String,
    ) : StackLensFailure() {
        override val wireMessage: String
            get() = "[REPO_DIR_NOT_FOUND:$path]"
    }

    data class MergeRequestConnectionRefused(
        val host: String,
    ) : StackLensFailure() {
        override val wireMessage: String
            get() = "[MR_CONN_REFUSED:$host]"
    }

    data class JiraConnectionRefused(
        val host: String,
    ) : StackLensFailure() {
        override val wireMessage: String
            get() = "[JIRA_CONN_REFUSED:$host]"
    }

    private companion object {
        const val QUOTA_RENDER_LIMIT = 500
    }
}
