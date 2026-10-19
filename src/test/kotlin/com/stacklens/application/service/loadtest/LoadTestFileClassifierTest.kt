package com.stacklens.application.service.loadtest

import com.stacklens.application.port.out.i18n.I18nPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LoadTestFileClassifierTest {
    private val i18n: I18nPort =
        object : I18nPort {
            override fun resolveLanguage(): String = "en"

            override fun t(
                key: String,
                vararg args: Any,
            ): String = if (key.endsWith("be_no_docker_files")) "no_docker" else "no_route"

            override fun tFor(
                lang: String,
                key: String,
                vararg args: Any,
            ): String = if (key.endsWith("be_no_docker_files")) "no_docker" else "no_route"

            override fun translateError(e: Exception): String = ""
        }

    @Test
    fun `dockerContents returns i18n fallback when no docker files`() {
        val files = listOf(LoadTestSourceFile.of("src/main.kt", "code"))
        assertEquals("no_docker", LoadTestFileClassifier.dockerContents(files, i18n))
    }

    @Test
    fun `dockerContents matches docker-compose files`() {
        val files = listOf(LoadTestSourceFile.of("docker-compose.yml", "version: 3"))
        val res = LoadTestFileClassifier.dockerContents(files, i18n)
        assertTrue(res.contains("version: 3"))
    }

    @Test
    fun `dockerContents matches Dockerfile`() {
        val files = listOf(LoadTestSourceFile.of("Dockerfile", "FROM alpine"))
        val res = LoadTestFileClassifier.dockerContents(files, i18n)
        assertTrue(res.contains("FROM alpine"))
    }

    @Test
    fun `routeContents returns i18n fallback when no route files`() {
        val files = listOf(LoadTestSourceFile.of("src/main.kt", "code"))
        assertEquals("no_route", LoadTestFileClassifier.routeContents(files, i18n))
    }

    @Test
    fun `routeContents matches Spring Boot controller by content`() {
        val files =
            listOf(
                LoadTestSourceFile.of(
                    "src/main/kotlin/x/UserController.kt",
                    "@RestController\nclass UserController { @GetMapping(\"/x\") fun x() {} }",
                ),
            )
        val res = LoadTestFileClassifier.routeContents(files, i18n)
        assertTrue(res.contains("UserController"))
    }

    @Test
    fun `routeContentsByName ignores controller annotations while routeContents does not`() {
        val files =
            listOf(
                LoadTestSourceFile(
                    matchPath = "/repo/src/Gateway.kt",
                    displayPath = "src/Gateway.kt",
                    content = "@RestController\nclass Gateway",
                ),
            )
        assertEquals("no_route", LoadTestFileClassifier.routeContentsByName(files, i18n))
        assertTrue(LoadTestFileClassifier.routeContents(files, i18n).contains("Gateway"))
    }

    @Test
    fun `routeContentsByName matches a controller file by its name`() {
        val files =
            listOf(
                LoadTestSourceFile(
                    matchPath = "/repo/src/UserController.kt",
                    displayPath = "src/UserController.kt",
                    content = "class UserController",
                ),
            )

        assertTrue(LoadTestFileClassifier.routeContentsByName(files, i18n).contains("UserController"))
    }

    @Test
    fun `dockerContents renders the display path while matching the absolute path`() {
        val files =
            listOf(
                LoadTestSourceFile(
                    matchPath = "/repos/docker-compose-cache/app/docker-compose.yml",
                    displayPath = "app/docker-compose.yml",
                    content = "version: \"3\"",
                ),
            )
        val res = LoadTestFileClassifier.dockerContents(files, i18n)
        assertTrue(res.contains("=== app/docker-compose.yml ==="))
    }

    @Test
    fun `isDockerFile matches Dockerfile in subdirectory`() {
        assertTrue(LoadTestFileClassifier.isDockerFile("subdir/Dockerfile", "Dockerfile"))
        assertTrue(LoadTestFileClassifier.isDockerFile("path/docker-compose.yml", "docker-compose.yml"))
    }
}
