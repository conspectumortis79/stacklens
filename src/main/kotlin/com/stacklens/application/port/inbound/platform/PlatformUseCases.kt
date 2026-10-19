package com.stacklens.application.port.inbound.platform

interface FactoryResetUseCase {
    fun factoryReset()
}

interface I18nUseCase {
    fun t(
        key: String,
        vararg args: Any,
    ): String

    fun tFor(
        lang: String,
        key: String,
        vararg args: Any,
    ): String

    fun translateError(e: Exception): String

    fun resolveLanguage(): String
}

interface AppKnowledgeUseCase {
    fun index(): String

    fun relevant(
        query: String,
        maxFiles: Int = 4,
        perFileChars: Int = 3000,
    ): String

    fun readme(): String?
}

data class MakeRunResult(
    val exitCode: Int,
    val timedOut: Boolean,
    val errorMessage: String? = null,
)

interface MakeRunnerUseCase {
    val allowedTargets: Set<String>

    fun run(
        repoPath: String,
        target: String,
        onLine: (String) -> Unit,
    ): MakeRunResult
}

data class InstallToolResult(
    val success: Boolean,
    val exitCode: Int?,
    val stdout: String,
    val stderr: String,
    val needsPrivilege: Boolean,
    val manualCommand: String?,
    val tools: List<Map<String, Any?>>,
)

interface EssentialToolsUseCase {
    fun getStatus(): Map<String, Any?>

    fun install(tool: String): InstallToolResult
}
