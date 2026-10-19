package com.stacklens.application.port.out.compiler

interface KotlinCompilerPort {
    fun executeWithCodeRetry(
        model: String,
        prompt: String,
    ): String

    fun compileWithRetry(
        toolId: Long,
        toolName: String,
        initialCode: String,
    ): Pair<String, String>

    fun compileToJar(
        toolId: Long,
        toolName: String,
        kotlinCode: String,
    ): String

    fun extractKotlinCode(response: String): String
}
