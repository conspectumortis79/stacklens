
package com.stacklens.application.service.codeanalysis
import com.stacklens.application.stereotype.ApplicationService

@ApplicationService
class MonorepoDetector {
    private val buildMarkers =
        setOf(
            "build.gradle.kts",
            "build.gradle",
            "pom.xml",
            "package.json",
            "composer.json",
            "pyproject.toml",
            "go.mod",
            "Cargo.toml",
            "settings.gradle.kts",
            "settings.gradle",
        )

    fun detectSubApps(allPaths: List<String>): List<Pair<String, String>> {
        val depth1 =
            allPaths
                .filter { path ->
                    val parts = path.split('/')
                    parts.size == 2 && buildMarkers.contains(parts[1])
                }.map { it.substringBefore('/') }
                .distinct()
        if (depth1.size >= 2) return depth1.map { Pair(it, it) }

        val depth2 =
            allPaths
                .filter { path ->
                    val parts = path.split('/')
                    parts.size == 3 && buildMarkers.contains(parts[2])
                }.map { path ->
                    val parts = path.split('/')
                    Pair("${parts[0]}/${parts[1]}", parts[1])
                }.distinctBy { it.first }
        if (depth2.size >= 2) return depth2

        return emptyList()
    }
}
