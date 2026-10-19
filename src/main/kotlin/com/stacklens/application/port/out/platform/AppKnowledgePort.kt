package com.stacklens.application.port.out.platform

interface AppKnowledgePort {
    fun index(): String

    fun relevant(
        query: String,
        maxFiles: Int,
        perFileChars: Int,
    ): String

    fun readme(): String?
}
