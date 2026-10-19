package com.stacklens.adapter.out.ai

object RepoFileTextToolCallParser {
    private val jacksonForArgs by lazy {
        com.fasterxml.jackson.databind
            .ObjectMapper()
    }

    fun parse(content: String): List<Map<String, Any>> {
        if (content.isBlank()) return emptyList()
        val results = mutableListOf<Map<String, Any>>()

        val fnRegex = Regex("""<function=([a-zA-Z_]+)>([\s\S]*?)</function>""")
        val paramRegex = Regex("""<parameter=([a-zA-Z_]+)>([\s\S]*?)</parameter>""")
        for (m in fnRegex.findAll(content)) {
            val name = m.groupValues[1].trim()
            val body = m.groupValues[2]
            val args = mutableMapOf<String, Any>()
            for (p in paramRegex.findAll(body)) {
                val k = p.groupValues[1].trim()
                var v = p.groupValues[2]
                if (v.startsWith("\n")) v = v.removePrefix("\n")
                if (v.endsWith("\n")) v = v.removeSuffix("\n")
                args[k] = coerceArgType(k, v)
            }
            results +=
                mapOf(
                    "id" to "call_text_${results.size}",
                    "function" to
                        mapOf(
                            "name" to name,
                            "arguments" to jacksonForArgs.writeValueAsString(args),
                        ),
                )
        }

        val fenceRegex = Regex("""```(?:tool_call|tool|json)\s*\n([\s\S]*?)\n```""")
        for (m in fenceRegex.findAll(content)) {
            val raw = m.groupValues[1].trim()
            runCatching {
                @Suppress("UNCHECKED_CAST")
                val parsed = jacksonForArgs.readValue(raw, Map::class.java) as Map<String, Any>
                val name = parsed["name"] as? String ?: return@runCatching
                val argsObj = parsed["arguments"] ?: parsed["parameters"] ?: emptyMap<String, Any>()
                val argsJson =
                    if (argsObj is String) argsObj else jacksonForArgs.writeValueAsString(argsObj)
                results +=
                    mapOf(
                        "id" to "call_text_${results.size}",
                        "function" to mapOf("name" to name, "arguments" to argsJson),
                    )
            }
        }

        val toolCallTagRegex = Regex("""<tool_call>\s*([\s\S]*?)\s*</tool_call>""")
        for (m in toolCallTagRegex.findAll(content)) {
            val raw = m.groupValues[1].trim()
            runCatching {
                @Suppress("UNCHECKED_CAST")
                val parsed = jacksonForArgs.readValue(raw, Map::class.java) as Map<String, Any>
                val name = parsed["name"] as? String ?: return@runCatching
                val argsObj = parsed["arguments"] ?: parsed["parameters"] ?: emptyMap<String, Any>()
                val argsJson =
                    if (argsObj is String) argsObj else jacksonForArgs.writeValueAsString(argsObj)
                results +=
                    mapOf(
                        "id" to "call_text_${results.size}",
                        "function" to mapOf("name" to name, "arguments" to argsJson),
                    )
            }
        }

        return results
    }

    private fun coerceArgType(
        key: String,
        raw: String,
    ): Any =
        when {
            key == "recursive" && raw.equals("true", ignoreCase = true) -> true
            key == "recursive" && raw.equals("false", ignoreCase = true) -> false
            else -> raw
        }
}
