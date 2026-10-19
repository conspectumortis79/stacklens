package com.stacklens.application.service.loadtest

import com.stacklens.application.port.out.i18n.I18nPort

internal object LoadTestFileClassifier {
    private val DOCKER_FILE_MATCHES: List<(name: String, path: String) -> Boolean> =
        listOf(
            { _, path -> path.contains("docker-compose") },
            { name, _ -> name == "dockerfile" },
            { name, _ -> name.startsWith("dockerfile.") },
        )

    fun isDockerFile(
        path: String,
        name: String,
    ): Boolean {
        val lower = name.lowercase()
        return DOCKER_FILE_MATCHES.any { it(lower, path) }
    }

    fun dockerContents(
        files: List<LoadTestSourceFile>,
        i18n: I18nPort,
    ): String = render(files, i18n, "load_test.be_no_docker_files") { isDockerFile(it.matchPath, it.name) }

    fun routeContents(
        files: List<LoadTestSourceFile>,
        i18n: I18nPort,
    ): String = render(files, i18n, "load_test.be_no_route_files") { RouteFileDetector.isRouteFileWithContent(it.matchPath, it.content) }

    fun routeContentsByName(
        files: List<LoadTestSourceFile>,
        i18n: I18nPort,
    ): String = render(files, i18n, "load_test.be_no_route_files") { RouteFileDetector.isRouteFileByName(it.matchPath, it.name) }

    private fun render(
        files: List<LoadTestSourceFile>,
        i18n: I18nPort,
        emptyKey: String,
        matches: (LoadTestSourceFile) -> Boolean,
    ): String =
        files
            .filter(matches)
            .joinToString("\n\n") { "=== ${it.displayPath} ===\n${it.content}" }
            .ifEmpty { i18n.t(emptyKey) }
}
