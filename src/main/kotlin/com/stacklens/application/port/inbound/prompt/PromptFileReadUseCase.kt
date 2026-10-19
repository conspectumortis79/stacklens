package com.stacklens.application.port.inbound.prompt

enum class PromptFileReadFailure {
    ACCESS_DENIED,
    NOT_FOUND,
    TOO_LARGE,
    READ_FAILED,
}

sealed interface PromptFileReadResult {
    data class Success(
        val content: String,
    ) : PromptFileReadResult

    data class Failed(
        val failure: PromptFileReadFailure,
        val message: String = "",
    ) : PromptFileReadResult
}

interface PromptFileReadUseCase {
    fun readPromptFile(path: String): PromptFileReadResult
}
