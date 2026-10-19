package com.stacklens.application.port.out.storage

interface DiagramStoragePort {
    fun saveDiagram(
        directory: String,
        filename: String,
        content: String,
    ): String
}

interface ReviewStoragePort {
    fun readPromptOverride(lang: String): String?

    fun savePromptOverride(
        lang: String,
        content: String,
    )

    fun deletePromptOverride(lang: String)
}
