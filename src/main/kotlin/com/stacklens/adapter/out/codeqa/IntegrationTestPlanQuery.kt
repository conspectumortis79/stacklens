package com.stacklens.adapter.out.codeqa

import com.stacklens.application.port.out.JsonReaderPort
import com.stacklens.application.port.out.MrFetchPort
import com.stacklens.application.port.out.ai.AiChatPort
import com.stacklens.application.port.out.file.FileAttributes
import com.stacklens.application.port.out.prompt.PromptReaderPort
import org.slf4j.LoggerFactory
import java.io.File

class IntegrationTestPlanQuery(
    private val copilotPort: AiChatPort,
    private val promptLoader: PromptReaderPort,
    private val jsonCodec: JsonReaderPort,
    private val fileSystem: FileAttributes,
) {
    private val log = LoggerFactory.getLogger(IntegrationTestPlanQuery::class.java)

    fun queryForTestPlan(
        mrData: MrFetchPort.MrData,
        composeContent: String,
        repoFileList: String,
        testInfraFiles: List<String>,
        composeDirRel: String,
    ): IntegrationTestPlan {
        val changed = mrData.changedFiles.take(60).joinToString("\n") { "- $it" }
        val projectTypes = detectProjectTypes(testInfraFiles)
        log.info(
            "[MR-IntegrationTest] Detected project types: {} (testInfra={}, changedFiles={})",
            projectTypes,
            testInfraFiles,
            mrData.changedFiles.size,
        )
        val prompt =
            promptLoader.get(
                "en",
                "codeqa.testPlan",
                mapOf(
                    "sourceBranch" to mrData.sourceBranch,
                    "targetBranch" to mrData.targetBranch,
                    "title" to mrData.title,
                    "changedFiles" to changed,
                    "changedFilesCount" to mrData.changedFiles.size.toString(),
                    "composeContent" to composeContent.take(8000),
                    "repoFileList" to repoFileList,
                    "testInfraInfo" to (if (testInfraFiles.isEmpty()) "none" else testInfraFiles.joinToString(", ")),
                    "projectTypes" to (if (projectTypes.isEmpty()) "unknown" else projectTypes.joinToString(", ")),
                    "composeDirNote" to if (composeDirRel.isBlank() || composeDirRel == ".") "At root" else "In `$composeDirRel`",
                ),
            )
        return try {
            val raw = copilotPort.execute(copilotPort.resolveModel(), prompt, 120L)
            val jsonText = Regex("""(?s)\{.*\}""").find(raw)?.value
            if (jsonText == null) {
                log.warn("[MR-IntegrationTest] AI returned no JSON for testPlan: {}", raw.take(300))
                return IntegrationTestPlan()
            }
            val plan = jsonCodec.decode(jsonText, IntegrationTestPlan::class)
            log.info(
                "[MR-IntegrationTest] AI test plan: projectTypes={}, strategy={}, services={}, commands={}, targets={}, reasoning={}",
                plan.projectTypes,
                plan.testStrategy,
                plan.affectedServices,
                plan.testCommands.size,
                plan.testTargets,
                plan.reasoning.take(200),
            )
            if (plan.testCommands.isEmpty() && plan.affectedServices.isNotEmpty()) {
                log.warn(
                    "[MR-IntegrationTest] AI returned services but no test commands for MR {} — falling back to health checks",
                    mrData.mrUrl,
                )
            }
            plan
        } catch (e: Exception) {
            log.error("[MR-IntegrationTest] Failed to query/decode AI test plan for MR {}: {}", mrData.mrUrl, e.message, e)
            IntegrationTestPlan()
        }
    }

    fun summarizeResults(
        mrData: MrFetchPort.MrData,
        plan: IntegrationTestPlan,
        outputs: List<com.stacklens.adapter.out.codeqa.DockerTestRunner.TestOutput>,
    ): String {
        val passed = outputs.count { it.passed }
        val total = outputs.size
        val summary =
            outputs.joinToString("\n---\n") {
                "Command: `${it.command}`\nExit: ${it.result.exitCode} (${if (it.passed) "OK" else "FAIL"})"
            }
        val prompt =
            promptLoader.get(
                "en",
                "codeqa.summarize",
                mapOf(
                    "title" to mrData.title,
                    "services" to plan.affectedServices.joinToString(", "),
                    "passed" to passed.toString(),
                    "total" to total.toString(),
                    "outputSummary" to summary,
                    "langInstruction" to promptLoader.get(copilotPort.resolveLanguage().ifBlank { "en" }, "common.langInstruction"),
                ),
            )
        return try {
            copilotPort.execute(copilotPort.resolveModel(), prompt, 90L)
        } catch (e: Exception) {
            "## Integration Test Results\n\n$passed/$total passed.\n\n_(AI summary: ${e.message})_"
        }
    }

    fun detectTestInfraFiles(dir: File): List<String> =
        listOf(
            "Makefile",
            "makefile",
            "package.json",
            "package-lock.json",
            "yarn.lock",
            "pnpm-lock.yaml",
            "jest.config.js",
            "jest.config.ts",
            "jest.config.cjs",
            "vitest.config.js",
            "vitest.config.ts",
            "cypress.json",
            "cypress.config.js",
            "playwright.config.js",
            "playwright.config.ts",
            "pytest.ini",
            "setup.cfg",
            "pyproject.toml",
            "tox.ini",
            "conftest.py",
            "Pipfile",
            "gradle.properties",
            "build.gradle",
            "build.gradle.kts",
            "settings.gradle",
            "settings.gradle.kts",
            "pom.xml",
            "build.sbt",
            "go.mod",
            "go.sum",
            "Cargo.toml",
            "Cargo.lock",
            "Gemfile",
            "Rakefile",
            ".rspec",
            "composer.json",
            "phpunit.xml",
            "phpunit.xml.dist",
            "phpunit.dist.xml",
            "*.csproj",
            "*.sln",
            "docker-compose.test.yml",
            "docker-compose.test.yaml",
            "docker-compose.ci.yml",
            "docker-compose.ci.yaml",
            "Dockerfile.test",
        ).filter { fileSystem.exists(File(dir, it)) }

    fun detectProjectTypes(testInfraFiles: List<String>): List<String> {
        val types = LinkedHashSet<String>()
        val nameSet = testInfraFiles.toSet()

        fun any(vararg needles: String) = needles.any { it in nameSet }
        if (any(
                "package.json",
                "yarn.lock",
                "pnpm-lock.yaml",
                "jest.config.js",
                "jest.config.ts",
                "jest.config.cjs",
                "vitest.config.js",
                "vitest.config.ts",
                "cypress.json",
                "cypress.config.js",
                "playwright.config.js",
                "playwright.config.ts",
            )
        ) {
            types += "node"
        }
        if (any("pytest.ini", "setup.cfg", "pyproject.toml", "tox.ini", "conftest.py", "Pipfile")) types += "python"
        if (any(
                "build.gradle",
                "build.gradle.kts",
                "settings.gradle",
                "settings.gradle.kts",
                "gradle.properties",
                "pom.xml",
                "build.sbt",
            )
        ) {
            types += "jvm"
        }
        if (any("go.mod", "go.sum")) types += "go"
        if (any("Cargo.toml", "Cargo.lock")) types += "rust"
        if (any("Gemfile", "Rakefile", ".rspec")) types += "ruby"
        if (any("composer.json", "phpunit.xml", "phpunit.xml.dist", "phpunit.dist.xml")) types += "php"
        if (testInfraFiles.any { it.endsWith(".csproj") || it.endsWith(".sln") }) types += "dotnet"
        if (testInfraFiles.any { it.startsWith("docker-compose.") || it == "Dockerfile.test" }) types += "docker"
        return types.toList()
    }
}
