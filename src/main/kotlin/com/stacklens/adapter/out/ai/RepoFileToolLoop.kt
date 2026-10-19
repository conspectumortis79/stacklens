package com.stacklens.adapter.out.ai

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

abstract class RepoFileToolLoop(
    private val maxIterations: Int,
) {
    protected val log: Logger = LoggerFactory.getLogger(javaClass)

    protected abstract val providerLabel: String

    protected abstract fun initialMessages(
        systemPrompt: String,
        userPrompt: String,
    ): List<Map<String, Any>>

    protected abstract fun handleIteration(
        iteration: Int,
        respJson: String,
        messages: MutableList<Map<String, Any>>,
        allText: StringBuilder,
        repoDir: File,
        onPartialOutput: (String) -> Unit,
    ): Boolean

    protected fun appendPartialOutput(
        text: String,
        allText: StringBuilder,
        onPartialOutput: (String) -> Unit,
    ) {
        if (text.isBlank()) return
        allText.append(text).append("\n")
        runCatching { onPartialOutput(allText.toString().trim()) }
    }

    protected fun logLoopDone(
        iteration: Int,
        allText: StringBuilder,
    ) {
        log.info(
            "[RepoFileTools] {} tool-loop done after {} iterations | text: {} chars",
            providerLabel,
            iteration,
            allText.length,
        )
    }

    protected fun logToolCalls(
        iteration: Int,
        count: Int,
        unit: String,
    ) {
        log.info("[RepoFileTools] {} iteration {}: {} {}", providerLabel, iteration, count, unit)
    }

    fun runLoop(
        model: String,
        systemPrompt: String,
        userPrompt: String,
        timeoutSeconds: Long,
        repoDir: File,
        onPartialOutput: (String) -> Unit,
        buildRequestBody: (messages: List<Map<String, Any>>) -> String,
        sendRequest: (requestBody: String, timeoutSeconds: Long) -> String,
        onComplete: (finalText: String) -> Unit = {},
        isCancelled: (() -> Boolean)? = null,
    ): String {
        val messages: MutableList<Map<String, Any>> = initialMessages(systemPrompt, userPrompt).toMutableList()

        val allText = StringBuilder()
        var iteration = 0

        log.info(
            "[RepoFileTools] {} tool-loop start | Repo: {} | Model: {} | Prompt: {} chars",
            providerLabel,
            repoDir,
            model,
            userPrompt.length,
        )

        while (iteration++ < maxIterations) {
            AiCancellation.checkCancelled(isCancelled, "$providerLabel tool loop")
            val reqBody = buildRequestBody(messages)
            val respJson = sendRequest(reqBody, timeoutSeconds)

            val keepGoing =
                handleIteration(
                    iteration = iteration,
                    respJson = respJson,
                    messages = messages,
                    allText = allText,
                    repoDir = repoDir,
                    onPartialOutput = onPartialOutput,
                )
            if (!keepGoing) break
        }

        val finalText = allText.toString().trim()
        onComplete(finalText)
        return finalText
    }
}
