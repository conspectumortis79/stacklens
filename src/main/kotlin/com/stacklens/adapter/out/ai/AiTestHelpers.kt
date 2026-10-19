package com.stacklens.adapter.out.ai

import com.stacklens.application.port.out.platform.ContainerRuntime
import com.stacklens.application.port.out.platform.ContainerRuntimeProbePort
import java.io.File

internal object AiTestHelpers {
    const val DEFAULT_DOCKER_ENV_PATH = "/.dockerenv"

    fun extractStringIds(
        dataList: List<*>?,
        key: String,
    ): List<String> {
        if (dataList == null) return emptyList()
        val out = mutableListOf<String>()
        for (item in dataList) {
            val raw: Any? =
                when (item) {
                    is com.fasterxml.jackson.databind.JsonNode -> item.get(key)
                    is Map<*, *> -> item[key]
                    else -> null
                } ?: continue
            val str =
                when (raw) {
                    is String -> raw
                    is com.fasterxml.jackson.databind.JsonNode -> {
                        if (raw.isTextual) raw.asText() else null
                    }
                    else -> raw.toString()
                }
            if (!str.isNullOrBlank()) out.add(str)
        }
        return out
    }

    fun nodeAsList(node: com.fasterxml.jackson.databind.JsonNode?): List<Any?>? {
        if (node == null || !node.isArray) return null
        val out = ArrayList<Any?>(node.size())
        for (i in 0 until node.size()) out.add(node.get(i))
        return out
    }

    fun maskApiKey(key: String): String {
        if (key.length <= 8) return "***"
        val prefix =
            when {
                key.startsWith("sk-proj-") -> "sk-proj-"
                key.startsWith("sk-") -> "sk-"
                else -> key.take(4)
            }
        return "$prefix...${key.takeLast(4)}"
    }

    fun resolveDockerUrl(
        url: String,
        containerRuntimeProbe: ContainerRuntimeProbePort?,
        dockerEnvPath: String = DEFAULT_DOCKER_ENV_PATH,
    ): String {
        val runtime =
            containerRuntimeProbe?.detectActiveRuntime()
                ?: if (File(dockerEnvPath).exists()) ContainerRuntime.DOCKER else ContainerRuntime.NONE
        if (runtime == ContainerRuntime.NONE) return url
        return url
            .replace("://localhost:", "://host.docker.internal:")
            .replace("://127.0.0.1:", "://host.docker.internal:")
    }
}
