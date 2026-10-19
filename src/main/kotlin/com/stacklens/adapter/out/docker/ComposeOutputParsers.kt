package com.stacklens.adapter.out.docker

import com.stacklens.domain.docker.ComposeContainerEntry
import com.stacklens.domain.docker.ComposeContainerState
import java.io.File

internal object ComposePsParser {
    const val FIELD_SEPARATOR: Char = '\t'
    const val MINIMUM_FIELDS: Int = 3

    fun parse(output: String): ComposeContainerState =
        ComposeContainerState(
            output
                .trim()
                .split('\n')
                .filter { it.isNotBlank() }
                .mapNotNull { line ->
                    val parts = line.split(FIELD_SEPARATOR)
                    if (parts.size < MINIMUM_FIELDS) {
                        null
                    } else {
                        ComposeContainerEntry(parts[0], parts[1], parts[2].toIntOrNull() ?: 0)
                    }
                },
        )
}

internal object DockerSocketCandidates {
    private val HOME_PATHS: List<String> =
        listOf(
            "/.docker/run/docker.sock",
            "/Library/Containers/com.docker.docker/Data/docker.sock",
            "/.docker/desktop/docker.sock",
        )

    const val SYSTEM_SOCKET: String = "/var/run/docker.sock"

    fun of(
        home: String,
        xdg: String?,
        uid: String?,
    ): List<String> =
        buildList {
            HOME_PATHS.forEach { add("$home$it") }
            add(SYSTEM_SOCKET)
            if (!xdg.isNullOrBlank()) add("$xdg/docker.sock")
            if (!uid.isNullOrBlank()) add("/run/user/$uid/docker.sock")
        }
}

internal object ProcSelfStatus {
    const val UID_PREFIX: String = "Uid:"

    fun uidOf(file: File): Pair<String?, String?> =
        runCatching {
            val line = file.readLines().firstOrNull { it.startsWith(UID_PREFIX) }
            Pair(line?.split("\t")?.getOrNull(1)?.trim(), line)
        }.getOrElse { Pair(null, null) }
}
