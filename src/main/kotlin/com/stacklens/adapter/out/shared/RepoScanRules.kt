package com.stacklens.adapter.out.shared

import java.io.File

data class RepoScanRules(
    val pipelineFileNames: Set<String> = DEFAULT_PIPELINE_FILE_NAMES,
    val codeExtensions: Set<String> = DEFAULT_CODE_EXTENSIONS,
    val skipDirs: Set<String> = DEFAULT_SKIP_DIRS,
    val pipelineDirectoryNames: Set<String> = DEFAULT_PIPELINE_DIR_NAMES,
) {
    fun isPipelineDirectory(dirName: String): Boolean {
        val lower = dirName.lowercase()
        return pipelineDirectoryNames.any { it == lower }
    }

    fun isPipelineFile(path: String): Boolean {
        val lower = path.replace('\\', '/').lowercase()
        val basename = lower.substringAfterLast('/')
        val parent = lower.substringBeforeLast('/')
        return basename in pipelineFileNames ||
            basename.startsWith("dockerfile") ||
            basename.startsWith("docker-compose.") ||
            basename.startsWith("compose.") ||
            basename.startsWith("buildkite.yml") ||
            basename.startsWith("buildkite.yaml") ||
            (basename.startsWith("buildkite.") && (basename.endsWith(".yml") || basename.endsWith(".yaml"))) ||
            ((basename.endsWith(".yml") || basename.endsWith(".yaml")) && parent.endsWith(".github/workflows")) ||
            ((basename == "config.yml" || basename == "config.yaml") && parent.endsWith(".circleci"))
    }

    fun isPriorityPipelineFile(file: File): Boolean {
        val lower = file.name.lowercase()
        return lower.startsWith("docker-compose") ||
            lower.startsWith("compose.") ||
            lower == ".gitlab-ci.yml" ||
            lower == ".gitlab-ci.yaml" ||
            lower == "jenkinsfile"
    }

    fun isCodeFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in codeExtensions
    }

    fun isPriorityFile(name: String): Boolean {
        val lower = name.lowercase()
        if (lower.startsWith("readme")) return true
        if (lower in PRIORITY_BASENAMES) return true
        if (lower.startsWith("main.") || lower.startsWith("app.") || lower.startsWith("index.")) return true
        if (lower.endsWith(".gradle.kts")) return true
        val base = lower.substringBeforeLast('.')
        if (PRIORITY_SUFFIXES.any { base.endsWith(it) }) return true
        if (PRIORITY_KEYWORDS.any { base.contains(it) }) return true
        return false
    }

    companion object {
        val DEFAULT_PIPELINE_FILE_NAMES: Set<String> =
            setOf(
                "dockerfile",
                "dockerfile.dev",
                "dockerfile.prod",
                "dockerfile.test",
                "docker-compose.yml",
                "docker-compose.yaml",
                "docker-compose.override.yml",
                "docker-compose.override.yaml",
                "docker-compose.dev.yml",
                "docker-compose.dev.yaml",
                "compose.yml",
                "compose.yaml",
                ".gitlab-ci.yml",
                ".gitlab-ci.yaml",
                "jenkinsfile",
                "bitbucket-pipelines.yml",
                "bitbucket-pipelines.yaml",
                ".travis.yml",
                ".travis.yaml",
                "azure-pipelines.yml",
                "azure-pipelines.yaml",
                "cloudbuild.yaml",
                "cloudbuild.yml",
                "buildkite.yml",
                "buildkite.yaml",
            )

        val DEFAULT_CODE_EXTENSIONS: Set<String> =
            setOf(
                "kt",
                "java",
                "groovy",
                "scala",
                "py",
                "ts",
                "tsx",
                "js",
                "jsx",
                "mjs",
                "go",
                "rs",
                "c",
                "cpp",
                "h",
                "hpp",
                "cs",
                "rb",
                "php",
                "swift",
                "dart",
                "elm",
                "sh",
                "bash",
                "zsh",
                "ps1",
                "bat",
                "sql",
                "xml",
                "json",
                "yaml",
                "yml",
                "toml",
                "properties",
                "env",
                "gradle",
                "kts",
                "md",
                "rst",
                "txt",
            )

        val DEFAULT_SKIP_DIRS: Set<String> =
            setOf(
                ".git",
                ".idea",
                ".vscode",
                ".github",
                "node_modules",
                "build",
                "target",
                "dist",
                "out",
                ".gradle",
                ".mvn",
                "__pycache__",
                ".pytest_cache",
                "venv",
                ".venv",
                ".env",
                "vendor",
                "bower_components",
                ".next",
                ".nuxt",
                "coverage",
                ".terraform",
                ".cache",
                "tmp",
                "temp",
            )

        val DEFAULT_PIPELINE_DIR_NAMES: Set<String> =
            setOf(".github", ".circleci", ".devcontainer")

        private val PRIORITY_BASENAMES: Set<String> =
            setOf(
                "build.gradle.kts",
                "build.gradle",
                "pom.xml",
                "package.json",
                "package-lock.json",
                "yarn.lock",
                "pnpm-lock.yaml",
                "cargo.toml",
                "go.mod",
                "go.sum",
                "pyproject.toml",
                "requirements.txt",
                "pipfile",
                "pipfile.lock",
                "composer.json",
                "composer.lock",
                "gemfile",
                "gemfile.lock",
                "settings.gradle.kts",
                "settings.gradle",
                "tsconfig.json",
                "next.config.js",
                "vite.config.js",
                "webpack.config.js",
                "dockerfile",
                "docker-compose.yml",
                "docker-compose.yaml",
                ".env",
                ".env.example",
                ".htaccess",
                "nginx.conf",
                "web.config",
                "phpunit.xml",
                "phpunit.xml.dist",
                "phpstan.neon",
                "psalm.xml",
            )

        private val PRIORITY_SUFFIXES: Set<String> =
            setOf(
                "controller",
                "handler",
                "router",
                "routes",
                "route",
                "middleware",
                "service",
                "repository",
                "dao",
                "model",
                "entity",
                "config",
                "security",
                "auth",
            )

        private val PRIORITY_KEYWORDS: Set<String> =
            setOf("login", "session", "permission", "guard")
    }
}
