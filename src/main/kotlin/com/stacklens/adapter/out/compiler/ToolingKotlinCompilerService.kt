package com.stacklens.adapter.out.compiler

import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.appconfig.StackLensConfigPort
import com.stacklens.application.port.out.compiler.JavaRuntimePort
import com.stacklens.application.port.out.compiler.KotlinCompilerPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.File
import java.nio.file.Files

@Component
class ToolingKotlinCompilerService(
    private val copilotCliService: CopilotPort,
    private val promptLoader: PromptReaderPort,
    private val i18n: I18nPort,
    private val stackLensConfig: StackLensConfigPort,
    private val compileRetryPolicy: ToolingCompileRetryPolicy,
    private val kotlincResolver: KotlincResolver = KotlincResolver(),
    private val fatJarBuilder: FatJarBuilder = FatJarBuilder(),
    private val javaHomeProvider: () -> String? = { System.getProperty("java.home") },
) : KotlinCompilerPort,
    JavaRuntimePort {
    private val log = LoggerFactory.getLogger(ToolingKotlinCompilerService::class.java)

    internal companion object {
        const val RETRY_PROMPT_KEY = "tooling.codeRetry"
        const val EXCERPT_LIMIT = 200
    }

    internal val embeddedKotlinc: EmbeddedKotlincInvoker by lazy {
        EmbeddedKotlincInvoker(
            i18n = i18n,
            kotlincResolver = kotlincResolver,
            fatJarBuilder = fatJarBuilder,
            javaExecutableProvider = this::findJavaExecutable,
        )
    }

    internal val jarRuntimeChecker: JarRuntimeChecker by lazy {
        JarRuntimeChecker(javaExecutableProvider = this::findJavaExecutable)
    }

    override fun executeWithCodeRetry(
        model: String,
        prompt: String,
    ): String {
        val firstResponse = copilotCliService.execute(model, prompt)
        return try {
            extractKotlinCode(firstResponse)
        } catch (_: IllegalStateException) {
            log.warn("First AI response contains no code block – retrying with format correction prompt.")
            val retryPrompt =
                promptLoader.get(
                    copilotCliService.resolveLanguage(),
                    RETRY_PROMPT_KEY,
                    mapOf("firstResponse" to firstResponse, "prompt" to prompt),
                )
            if (retryPrompt == RETRY_PROMPT_KEY) {
                val msg =
                    "Prompt template '$RETRY_PROMPT_KEY' not found in custom overrides, " +
                        "language-specific prompts, or 'en' fallback. " +
                        "Check that the prompt file exists and the configured AI language is correct."
                log.error(msg)
                throw IllegalStateException(msg)
            }
            log.debug("Retry prompt sent to LLM ({} chars): {}", retryPrompt.length, retryPrompt)
            val retryResponse = copilotCliService.execute(model, retryPrompt)
            extractKotlinCode(retryResponse)
        }
    }

    override fun compileWithRetry(
        toolId: Long,
        toolName: String,
        initialCode: String,
    ): Pair<String, String> =
        compileRetryPolicy.compileWithRetry(
            toolId = toolId,
            initialCode = initialCode,
            compile = { code -> compileToJar(toolId, toolName, code) },
            checkRuntime = jarRuntimeChecker::check,
            correctCode = ::executeWithCodeRetry,
        )

    override fun extractKotlinCode(response: String): String {
        val kotlinBlock = Regex("```kotlin\\s*\\n([\\s\\S]*?)\\n```", RegexOption.MULTILINE).find(response)
        if (kotlinBlock != null) return kotlinBlock.groupValues[1].trim()

        val genericBlock = Regex("```\\w*\\s*\\n([\\s\\S]*?)\\n```", RegexOption.MULTILINE).find(response)
        if (genericBlock != null) return genericBlock.groupValues[1].trim()

        val inlineBlock = Regex("```(?:kotlin)?\\s*([\\s\\S]*?)```").find(response)
        if (inlineBlock != null) {
            val code = inlineBlock.groupValues[1].trim()
            if (code.isNotEmpty()) return code
        }

        val excerpt = response.take(EXCERPT_LIMIT).ifBlank { "<empty>" }
        log.warn(
            "AI response has no fenced code block ({} chars). Response excerpt: {}",
            response.length,
            excerpt,
        )
        throw IllegalStateException(
            i18n.t("tooling.be_no_kotlin_codeblock", excerpt),
        )
    }

    override fun compileToJar(
        toolId: Long,
        toolName: String,
        kotlinCode: String,
    ): String {
        val safeName = toolName.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(50)
        val workDir = Files.createTempDirectory("stacklens-tool-$toolId-").toFile()
        val outputDir = File(stackLensConfig.dataDir, "tools").apply { mkdirs() }
        val sourceFile = File(workDir, "Main.kt")
        val jarFile = File(outputDir, "tool-$toolId-$safeName.jar")

        try {
            val sanitizedCode =
                kotlinCode
                    .replace(Regex("""(?m)^[ \t]*package[ \t]+\S[^\n]*\n?"""), "")
                    .trimStart()
            sourceFile.writeText(sanitizedCode)

            compileSourceToFile(jarFile, sourceFile, workDir)

            return jarFile.absolutePath
        } finally {
            workDir.deleteRecursively()
        }
    }

    internal fun compileSourceToFile(
        jarFile: File,
        sourceFile: File,
        workDir: File,
    ) {
        val kotlinc = kotlincResolver.findKotlinc()
        if (kotlinc != null) {
            log.info("Compiling with external kotlinc: {}", kotlinc)
            embeddedKotlinc.compileWithExternalKotlinc(kotlinc, jarFile, sourceFile, workDir)
            fatJarBuilder.ensureMainManifest(jarFile)
        } else {
            log.info("Kein externes kotlinc gefunden – verwende eingebetteten K2JVMCompiler")
            embeddedKotlinc.compileWithEmbeddedCompiler(jarFile, sourceFile)
        }
    }

    override fun findJavaExecutable(): String {
        val javaHome = javaHomeProvider()
        if (!javaHome.isNullOrBlank()) {
            val javaBin = File(javaHome, "bin/java")
            if (javaBin.exists() && javaBin.canExecute()) return javaBin.absolutePath
        }
        return "java"
    }

    fun findKotlinc(): String? = kotlincResolver.findKotlinc()
}
