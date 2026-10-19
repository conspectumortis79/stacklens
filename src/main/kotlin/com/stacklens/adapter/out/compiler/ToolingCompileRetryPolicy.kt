package com.stacklens.adapter.out.compiler

import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ToolingCompileRetryPolicy(
    private val copilotCliService: CopilotPort,
    private val promptLoader: PromptReaderPort,
) {
    private val log = LoggerFactory.getLogger(ToolingCompileRetryPolicy::class.java)

    fun compileWithRetry(
        toolId: Long,
        initialCode: String,
        compile: (String) -> String,
        checkRuntime: (String) -> String?,
        correctCode: (String, String) -> String,
    ): Pair<String, String> {
        val maxRetries = 2
        var code = initialCode
        var lastError: String? = null
        var failure = IllegalStateException("Tooling $toolId compilation failed")
        var isRuntimeError = false
        val model = copilotCliService.resolveModel()
        val lang = copilotCliService.resolveLanguage()

        for (attempt in 0..maxRetries) {
            if (lastError != null) {
                val errorSnapshot = lastError
                val promptKey = if (isRuntimeError) "tooling.runtimeErrorRetry" else "tooling.compileErrorRetry"
                if (isRuntimeError) {
                    log.warn(
                        "[Tooling {}] Runtime error (attempt {}), sending error to AI for correction",
                        toolId,
                        attempt,
                    )
                } else {
                    log.warn(
                        "[Tooling {}] Compile error (attempt {}), sending error to AI for correction",
                        toolId,
                        attempt,
                    )
                }
                val fixPrompt =
                    promptLoader.get(
                        lang,
                        promptKey,
                        mapOf("code" to code, "errors" to errorSnapshot.take(3000)),
                    )
                val correctedCode =
                    try {
                        correctCode(model, fixPrompt)
                    } catch (e: IllegalStateException) {
                        failure = e
                        log.warn(
                            "[Tooling {}] AI correction returned no usable code (attempt {}): {}",
                            toolId,
                            attempt,
                            e.message,
                        )
                        continue
                    }
                code = correctedCode
            }
            try {
                val jarPath = compile(code)
                val runtimeError = checkRuntime(jarPath)
                if (runtimeError == null) {
                    if (attempt > 0) log.info("[Tooling {}] Compilation successful after {} retry(s)", toolId, attempt)
                    return Pair(code, jarPath)
                }
                isRuntimeError = true
                lastError = runtimeError
                failure = IllegalStateException("Runtime error after $attempt retry(s):\n$runtimeError")
            } catch (e: IllegalStateException) {
                isRuntimeError = false
                lastError = e.message
                failure = e
            }
        }
        log.error("[Tooling {}] Compilation not resolved after {} retry(s)", toolId, maxRetries)
        throw failure
    }
}
