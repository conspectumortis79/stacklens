package com.stacklens.application.port.out.loadtest

data class LoadTestEnvironment(
    val name: String,
    val host: String,
    val port: String,
    val scheme: String,
)

interface LoadTestPromptPort {
    fun buildAnalysisPrompt(
        lang: String,
        projectName: String,
        projectPath: String,
        projectTree: String,
        fileContents: String,
    ): String

    fun buildK6Prompt(
        lang: String,
        projectName: String,
        findingsJson: String,
        dockerFiles: String = "",
        routeFiles: String = "",
    ): String

    fun buildEnvDetectPrompt(
        lang: String,
        projectName: String,
        dockerFiles: String = "",
        routeFiles: String = "",
    ): String
}

interface LoadTestResponseExtractionPort {
    fun extractSection(
        text: String,
        startMarker: String,
        endMarker: String,
    ): String

    fun extractJsonBlock(
        text: String,
        startMarker: String,
        endMarker: String,
    ): String?

    fun extractJsonArray(text: String): String?

    fun extractCodeBlock(
        text: String,
        vararg langs: String,
    ): String?
}

interface LoadTestEnvironmentPort {
    fun parseEnvironments(json: String?): List<LoadTestEnvironment>

    fun patchK6BaseUrl(
        script: String,
        env: LoadTestEnvironment,
    ): String
}

interface LoadTestAnalysisAiPort :
    LoadTestPromptPort,
    LoadTestResponseExtractionPort
