package com.stacklens.application.service

import com.stacklens.application.port.inbound.chat.ChatRequest
import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.application.stereotype.ApplicationService

@ApplicationService
class KiChatMcpPreflight(
    private val mcpConfigs: McpServerConfigPort,
    private val mcpGateway: McpGatewayPort,
    private val jsonCodec: JsonCodecPort,
    private val copilot: CopilotPort,
    private val promptLoader: PromptReaderPort,
) : McpChatPreflight {
    override fun run(
        model: String,
        req: ChatRequest,
    ): String {
        val config = mcpConfigs.findFirstActiveMcpServerConfig() ?: return ""

        val tools = runCatching { mcpGateway.listTools(config) }.getOrDefault(emptyList())
        if (tools.isEmpty()) return ""

        val toolList = tools.joinToString("\n") { "- ${it.name}: ${it.description.take(120)}" }
        val planPrompt =
            promptLoader.get(
                "en",
                "kichat.mcpToolPlan",
                mapOf("toolList" to toolList, "userMessage" to req.userMessage.take(500)),
            )

        val planJson = runCatching { copilot.execute(model, planPrompt, 30L).trim() }.getOrDefault("[]")
        val cleanJson =
            planJson
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
                .let { raw ->
                    val start = raw.indexOf('[')
                    val end = raw.lastIndexOf(']')
                    if (start >= 0 && end > start) raw.substring(start, end + 1) else "[]"
                }

        @Suppress("UNCHECKED_CAST")
        val calls =
            runCatching {
                jsonCodec.parseArrayOrEmpty(cleanJson).filterIsInstance<Map<String, Any?>>()
            }.getOrDefault(emptyList())

        if (calls.isEmpty()) return ""

        val sb = StringBuilder()
        for (call in calls.take(5)) {
            val toolName = call["tool"] as? String ?: continue

            @Suppress("UNCHECKED_CAST")
            val args = (call["args"] as? Map<String, Any?>) ?: emptyMap()
            val result = runCatching { mcpGateway.callTool(config, toolName, args) }.getOrNull() ?: continue
            sb.appendLine("### Tool: $toolName")
            sb.appendLine(result.text.take(4000))
            sb.appendLine()
        }
        return sb.toString().trim()
    }
}
