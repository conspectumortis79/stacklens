package com.stacklens.adapter.out.codeqa

import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.port.out.MrFetchPort
import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.Files
import java.nio.file.Path

class IntegrationTestPlanQueryTest {
    private val copilotPort = mock<CopilotStreamingPort>()
    private val promptLoader = mock<PromptReaderPort>()
    private val jsonCodec = mock<JsonCodecPort>()

    private fun newQuery(): IntegrationTestPlanQuery = IntegrationTestPlanQuery(copilotPort, promptLoader, jsonCodec, JavaFileSystemAdapter())

    @Test
    fun `detectTestInfraFiles returns an empty list when the directory has no infra files`(
        @TempDir tmp: Path,
    ) {
        assertTrue(newQuery().detectTestInfraFiles(tmp.toFile()).isEmpty())
    }

    @Test
    fun `detectTestInfraFiles lists Makefile when present`(
        @TempDir tmp: Path,
    ) {
        Files.createFile(tmp.resolve("Makefile"))
        val found = newQuery().detectTestInfraFiles(tmp.toFile())
        assertTrue("Makefile" in found)
    }

    @Test
    fun `detectTestInfraFiles lists both makefile cases`(
        @TempDir tmp: Path,
    ) {
        Files.createFile(tmp.resolve("makefile"))
        val found = newQuery().detectTestInfraFiles(tmp.toFile())
        assertTrue("makefile" in found)
    }

    @Test
    fun `detectTestInfraFiles lists Node, Python, JVM, Go, Rust, Ruby, PHP, Docker, and dotnet infra files`(
        @TempDir tmp: Path,
    ) {
        Files.createFile(tmp.resolve("package.json"))
        Files.createFile(tmp.resolve("pytest.ini"))
        Files.createFile(tmp.resolve("build.gradle.kts"))
        Files.createFile(tmp.resolve("go.mod"))
        Files.createFile(tmp.resolve("Cargo.toml"))
        Files.createFile(tmp.resolve("Gemfile"))
        Files.createFile(tmp.resolve("composer.json"))
        Files.createFile(tmp.resolve("docker-compose.test.yml"))
        val found = newQuery().detectTestInfraFiles(tmp.toFile())
        for (expected in listOf("package.json", "pytest.ini", "build.gradle.kts", "go.mod", "Cargo.toml", "Gemfile", "composer.json", "docker-compose.test.yml")) {
            assertTrue(expected in found, "expected $expected in $found")
        }
    }

    @Test
    fun `detectProjectTypes returns node for package_json`() {
        assertEquals(listOf("node"), newQuery().detectProjectTypes(listOf("package.json")))
    }

    @Test
    fun `detectProjectTypes returns node for vitest config`() {
        assertEquals(listOf("node"), newQuery().detectProjectTypes(listOf("vitest.config.ts")))
    }

    @Test
    fun `detectProjectTypes returns python for pytest_ini`() {
        assertEquals(listOf("python"), newQuery().detectProjectTypes(listOf("pytest.ini")))
    }

    @Test
    fun `detectProjectTypes returns jvm for build_gradle_kts`() {
        assertEquals(listOf("jvm"), newQuery().detectProjectTypes(listOf("build.gradle.kts")))
    }

    @Test
    fun `detectProjectTypes returns jvm for settings_gradle`() {
        assertEquals(listOf("jvm"), newQuery().detectProjectTypes(listOf("settings.gradle")))
    }

    @Test
    fun `detectProjectTypes returns jvm for pom_xml`() {
        assertEquals(listOf("jvm"), newQuery().detectProjectTypes(listOf("pom.xml")))
    }

    @Test
    fun `detectProjectTypes returns jvm for gradle_properties`() {
        assertEquals(listOf("jvm"), newQuery().detectProjectTypes(listOf("gradle.properties")))
    }

    @Test
    fun `detectProjectTypes returns jvm for build_sbt`() {
        assertEquals(listOf("jvm"), newQuery().detectProjectTypes(listOf("build.sbt")))
    }

    @Test
    fun `detectProjectTypes returns go for go_mod`() {
        assertEquals(listOf("go"), newQuery().detectProjectTypes(listOf("go.mod")))
    }

    @Test
    fun `detectProjectTypes returns go for go_sum`() {
        assertEquals(listOf("go"), newQuery().detectProjectTypes(listOf("go.sum")))
    }

    @Test
    fun `detectProjectTypes returns rust for cargo_lock`() {
        assertEquals(listOf("rust"), newQuery().detectProjectTypes(listOf("Cargo.lock")))
    }

    @Test
    fun `detectProjectTypes returns ruby for rspec`() {
        assertEquals(listOf("ruby"), newQuery().detectProjectTypes(listOf(".rspec")))
    }

    @Test
    fun `detectProjectTypes returns ruby for rakefile`() {
        assertEquals(listOf("ruby"), newQuery().detectProjectTypes(listOf("Rakefile")))
    }

    @Test
    fun `detectProjectTypes returns php for phpunit dist xml`() {
        assertEquals(listOf("php"), newQuery().detectProjectTypes(listOf("phpunit.dist.xml")))
    }

    @Test
    fun `detectProjectTypes returns dotnet for csproj file`() {
        assertEquals(listOf("dotnet"), newQuery().detectProjectTypes(listOf("Foo.csproj")))
    }

    @Test
    fun `detectProjectTypes returns dotnet for sln file`() {
        assertEquals(listOf("dotnet"), newQuery().detectProjectTypes(listOf("Foo.sln")))
    }

    @Test
    fun `detectProjectTypes returns docker for docker-compose ci yaml`() {
        assertEquals(listOf("docker"), newQuery().detectProjectTypes(listOf("docker-compose.ci.yaml")))
    }

    @Test
    fun `detectProjectTypes returns docker for Dockerfile test`() {
        assertEquals(listOf("docker"), newQuery().detectProjectTypes(listOf("Dockerfile.test")))
    }

    @Test
    fun `detectProjectTypes returns multiple types for a multi-language project`() {
        val types =
            newQuery()
                .detectProjectTypes(listOf("package.json", "build.gradle.kts", "Dockerfile.test"))
        assertEquals(listOf("node", "jvm", "docker"), types)
    }

    @Test
    fun `detectProjectTypes returns an empty list for unknown file names`() {
        assertTrue(newQuery().detectProjectTypes(listOf("README.md", "LICENSE")).isEmpty())
    }

    @Test
    fun `queryForTestPlan returns an empty plan when the AI throws`() {
        whenever(promptLoader.get(eq("en"), any(), any())).thenReturn("prompt text")
        whenever(copilotPort.resolveModel()).thenReturn("test-model")
        whenever(copilotPort.execute(any(), any(), any(), anyOrNull())).thenThrow(RuntimeException("AI exploded"))
        val mrData = sampleMrData()
        val plan = newQuery().queryForTestPlan(mrData, composeContent = "", repoFileList = "", testInfraFiles = emptyList(), composeDirRel = ".")
        assertNotNull(plan)
        assertTrue(plan.affectedServices.isEmpty())
    }

    @Test
    fun `summarizeResults returns a Markdown fallback when the AI throws`() {
        whenever(promptLoader.get(eq("en"), any(), any())).thenReturn("prompt text")
        whenever(copilotPort.resolveModel()).thenReturn("test-model")
        whenever(copilotPort.resolveLanguage()).thenReturn("en")
        whenever(copilotPort.execute(any(), any(), any(), anyOrNull())).thenThrow(RuntimeException("AI exploded"))
        val mrData = sampleMrData()
        val plan = IntegrationTestPlan()
        val out = newQuery().summarizeResults(mrData, plan, outputs = emptyList())
        assertTrue(out.contains("passed"))
        assertTrue(out.contains("AI summary"))
    }

    @Test
    fun `summarizeResults falls back to en when resolveLanguage is blank`() {
        whenever(promptLoader.get(eq("en"), any(), any())).thenReturn("prompt text")
        whenever(copilotPort.resolveModel()).thenReturn("test-model")
        whenever(copilotPort.resolveLanguage()).thenReturn("   ")
        whenever(copilotPort.execute(any(), any(), any(), anyOrNull())).thenReturn("summary-ok")
        val mrData = sampleMrData()
        val plan = IntegrationTestPlan()
        val out = newQuery().summarizeResults(mrData, plan, outputs = emptyList())
        assertEquals("summary-ok", out)
    }

    private fun sampleMrData(): MrFetchPort.MrData =
        MrFetchPort.MrData(
            mrUrl = "https://example.com/mr/1",
            platform = "gitlab",
            title = "Test MR",
            description = "",
            sourceBranch = "feature",
            targetBranch = "main",
            authorName = "tester",
            state = "open",
            diffText = "",
            changedFiles = emptyList(),
        )
}
