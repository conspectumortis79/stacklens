package com.stacklens.application.service.loadtest

import com.stacklens.application.port.out.i18n.I18nPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class AiAnalysisRunnerHelpersTest {
    private val runner =
        testAiAnalysisRunner(
            analysisRepo = org.mockito.kotlin.mock(),
            gitlabConfigRepo = org.mockito.kotlin.mock(),
            copilotPort = org.mockito.kotlin.mock(),
            gitlabService = org.mockito.kotlin.mock(),
            i18n = org.mockito.kotlin.mock(),
            aiService = org.mockito.kotlin.mock(),
            localRepoConfigRepo = org.mockito.kotlin.mock(),
            githubRepoConfigRepo = org.mockito.kotlin.mock(),
            scanService = org.mockito.kotlin.mock(),
            githubBridge = org.mockito.kotlin.mock(),
            copilotConfig = org.mockito.kotlin.mock(),
            localDirectoryScan = org.mockito.kotlin.mock(),
            asyncExecutor = com.stacklens.testsupport.InlineAsyncExecutor(),
            parallelTasks = com.stacklens.adapter.out.async.ForkJoinParallelTaskPort,
            clock = com.stacklens.adapter.out.time.SystemClockPort,
        )

    private val i18n: I18nPort =
        object : I18nPort {
            override fun resolveLanguage(): String = "en"

            override fun t(
                key: String,
                vararg args: Any,
            ): String = key

            override fun tFor(
                lang: String,
                key: String,
                vararg args: Any,
            ): String = key

            override fun translateError(e: Exception): String = e.message ?: ""
        }

    @Test
    fun `splitGitlabFiles returns i18n fallback when no docker files`() {
        val files = listOf("src/main.kt" to "code", "README.md" to "readme")
        val (docker, routes) = runner.splitGitlabFiles(files, i18n)
        assertEquals("load_test.be_no_docker_files", docker)
        assertTrue(routes.isNotBlank())
    }

    @Test
    fun `splitGitlabFiles matches docker-compose files`() {
        val files = listOf("docker-compose.yml" to "version: 3")
        val (docker, _) = runner.splitGitlabFiles(files, i18n)
        assertTrue(docker.contains("version: 3"))
    }

    @Test
    fun `splitGitlabFiles matches Dockerfile`() {
        val files = listOf("Dockerfile" to "FROM alpine")
        val (docker, _) = runner.splitGitlabFiles(files, i18n)
        assertTrue(docker.contains("FROM alpine"))
    }

    @Test
    fun `splitGitlabFiles matches Dockerfile with extension`() {
        val files = listOf("Dockerfile.dev" to "FROM alpine")
        val (docker, _) = runner.splitGitlabFiles(files, i18n)
        assertTrue(docker.contains("FROM alpine"))
    }

    @Test
    fun `splitGitlabFiles matches Dockerfile in subdirectory`() {
        val files = listOf("subdir/Dockerfile" to "FROM alpine")
        val (docker, _) = runner.splitGitlabFiles(files, i18n)
        assertTrue(docker.contains("FROM alpine"))
    }

    @Test
    fun `splitGitlabFiles matches routes pattern`() {
        val files = listOf("src/routes/web.php" to "<?php")
        val (docker, routes) = runner.splitGitlabFiles(files, i18n)
        assertEquals("load_test.be_no_docker_files", docker)
        assertTrue(routes.contains("<?php"))
    }

    @Test
    fun `splitGitlabFiles matches api_php route`() {
        val files = listOf("api.php" to "<?php route")
        val (_, routes) = runner.splitGitlabFiles(files, i18n)
        assertTrue(routes.contains("route"))
    }

    @Test
    fun `splitGitlabFiles matches urls_py route`() {
        val files = listOf("urls.py" to "urlpatterns = []")
        val (_, routes) = runner.splitGitlabFiles(files, i18n)
        assertTrue(routes.contains("urlpatterns"))
    }

    @Test
    fun `splitGitlabFiles matches router_js route`() {
        val files = listOf("src/router.js" to "export default")
        val (_, routes) = runner.splitGitlabFiles(files, i18n)
        assertTrue(routes.contains("export default"))
    }

    @Test
    fun `splitGitlabFiles matches router_ts route`() {
        val files = listOf("src/router.ts" to "export const")
        val (_, routes) = runner.splitGitlabFiles(files, i18n)
        assertTrue(routes.contains("export const"))
    }

    @Test
    fun `splitGitlabFiles matches routing pattern`() {
        val files = listOf("src/app/routing.py" to "routes = []")
        val (_, routes) = runner.splitGitlabFiles(files, i18n)
        assertTrue(routes.contains("routes"))
    }

    @Test
    fun `splitGitlabFiles matches Spring Boot Kotlin controller`() {
        val files =
            listOf(
                "src/main/kotlin/com/example/AdminController.kt" to
                    "@RestController\nclass AdminController { @GetMapping(\"/api/admin\") fun x() {} }",
            )
        val (_, routes) = runner.splitGitlabFiles(files, i18n)
        assertTrue(routes.contains("AdminController"))
    }

    @Test
    fun `splitGitlabFiles matches Spring Boot Java controller by name`() {
        val files = listOf("src/main/java/com/example/UserController.java" to "class UserController {}")
        val (_, routes) = runner.splitGitlabFiles(files, i18n)
        assertTrue(routes.contains("UserController"))
    }

    @Test
    fun `splitGitlabFiles matches Spring Boot Kotlin file by content signature`() {
        val files =
            listOf(
                "src/main/kotlin/com/example/WebConfig.kt" to
                    "@Configuration\nclass WebConfig { @Bean fun view() { @GetMapping(\"/x\") } }",
            )
        val (_, routes) = runner.splitGitlabFiles(files, i18n)
        assertTrue(routes.contains("WebConfig"))
    }

    @Test
    fun `splitGitlabFiles ignores non-route Kotlin files`() {
        val files = listOf("src/main/kotlin/com/example/UserService.kt" to "class UserService {}")
        val (_, routes) = runner.splitGitlabFiles(files, i18n)
        assertEquals("load_test.be_no_route_files", routes)
    }

    @Test
    fun `splitGitlabFiles returns i18n fallback when no route files`() {
        val files = listOf("Dockerfile" to "FROM alpine")
        val (docker, routes) = runner.splitGitlabFiles(files, i18n)
        assertTrue(docker.contains("FROM alpine"))
        assertEquals("load_test.be_no_route_files", routes)
    }

    @Test
    fun `splitGitlabFiles joins multiple docker files with separator`() {
        val files =
            listOf(
                "Dockerfile" to "FROM alpine",
                "docker-compose.yml" to "version: 3",
            )
        val (docker, _) = runner.splitGitlabFiles(files, i18n)
        assertTrue(docker.contains("FROM alpine"))
        assertTrue(docker.contains("version: 3"))
        assertTrue(docker.contains("==="))
    }
}
