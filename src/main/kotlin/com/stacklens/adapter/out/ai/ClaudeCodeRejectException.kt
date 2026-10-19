package com.stacklens.adapter.out.ai

class ClaudeCodeRejectException(
    val status: Int,
    val responseBody: String,
) : IllegalStateException("ClaudeCode error (HTTP $status): ${responseBody.take(BODY_LIMIT)}") {
    companion object {
        const val BODY_LIMIT: Int = 400
    }
}

object ClaudeCodeRejections {
    private val CREDENTIAL_STATUSES: Set<Int> = setOf(401, 402, 403)

    private const val CREDIT_MARKER: String = "credit balance is too low"

    fun isBilling(
        status: Int,
        body: String,
    ): Boolean = status == 402 || body.contains(CREDIT_MARKER, ignoreCase = true)

    fun isCredentialFault(
        status: Int,
        body: String,
    ): Boolean = status in CREDENTIAL_STATUSES || isBilling(status, body)
}
