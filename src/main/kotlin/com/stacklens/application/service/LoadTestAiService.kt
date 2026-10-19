package com.stacklens.application.service

import com.stacklens.application.port.out.AiResponseJsonExtractorPort
import com.stacklens.application.port.out.loadtest.LoadTestEnvironment
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.application.service.loadtest.LoadTestAi
import com.stacklens.application.stereotype.ApplicationService

@ApplicationService
class LoadTestAiService(
    private val promptLoader: PromptReaderPort,
    private val aiJsonExtractor: AiResponseJsonExtractorPort,
) : LoadTestAi {
    override fun buildAnalysisPrompt(
        lang: String,
        projectName: String,
        projectPath: String,
        projectTree: String,
        fileContents: String,
    ): String =
        promptLoader.get(
            lang,
            "loadtest.analysisPrompt",
            mapOf(
                "projectName" to projectName,
                "projectPath" to projectPath,
                "projectTree" to projectTree.take(3_000),
                "fileContents" to fileContents.take(30_000),
            ),
        )

    override fun buildK6Prompt(
        lang: String,
        projectName: String,
        findingsJson: String,
        dockerFiles: String,
        routeFiles: String,
    ): String =
        promptLoader.get(
            lang,
            "loadtest.k6Prompt",
            mapOf(
                "projectName" to projectName,
                "findingsJson" to findingsJson.take(8_000),
                "dockerFiles" to dockerFiles.take(5_000),
                "routeFiles" to routeFiles.take(8_000),
            ),
        )

    override fun buildEnvDetectPrompt(
        lang: String,
        projectName: String,
        dockerFiles: String,
        routeFiles: String,
    ): String =
        promptLoader.get(
            lang,
            "loadtest.envDetectPrompt",
            mapOf(
                "projectName" to projectName,
                "dockerFiles" to dockerFiles.take(6_000),
                "routeFiles" to routeFiles.take(4_000),
            ),
        )

    override fun parseEnvironments(json: String?): List<LoadTestEnvironment> {
        if (json.isNullOrBlank()) return emptyList()
        return Regex("""\{([^}]+)\}""")
            .findAll(json)
            .map { match ->
                val obj = match.groupValues[1]

                fun field(key: String): String = Regex(""""$key"\s*:\s*"([^"]*)"""").find(obj)?.groupValues?.get(1) ?: ""
                LoadTestEnvironment(field("name"), field("host"), field("port"), field("scheme"))
            }.filter { it.name.isNotEmpty() && it.host.isNotEmpty() }
            .toList()
    }

    override fun patchK6BaseUrl(
        script: String,
        env: LoadTestEnvironment,
    ): String {
        val port = env.port
        val stdPort = (env.scheme == "http" && port == "80") || (env.scheme == "https" && port == "443")
        val newUrl = "${env.scheme}://${env.host}${if (!stdPort && port.isNotBlank()) ":$port" else ""}"
        return script.replace(
            Regex("""(?m)(const|let|var)\s+BASE_URL\s*=\s*(?:__ENV\.BASE_URL\s*\|\|\s*)?['"][^'"]*['"]"""),
            "const BASE_URL = '$newUrl'",
        )
    }

    override fun extractSection(
        text: String,
        startMarker: String,
        endMarker: String,
    ): String = aiJsonExtractor.extractSection(text, startMarker, endMarker)

    override fun extractJsonBlock(
        text: String,
        startMarker: String,
        endMarker: String,
    ): String? = aiJsonExtractor.extractJsonBlock(text, startMarker, endMarker)

    override fun extractJsonArray(text: String): String? = aiJsonExtractor.extractJsonArray(text)

    override fun extractCodeBlock(
        text: String,
        vararg langs: String,
    ): String? = aiJsonExtractor.extractCodeBlock(text, *langs)
}
