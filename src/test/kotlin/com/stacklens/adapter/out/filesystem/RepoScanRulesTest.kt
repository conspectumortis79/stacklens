package com.stacklens.adapter.out.filesystem

import com.stacklens.adapter.out.shared.RepoScanRules
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class RepoScanRulesTest {
    private val rules = RepoScanRules()

    @Test
    fun `isPipelineFile matches canonical pipeline filenames`() {
        assertTrue(rules.isPipelineFile("Dockerfile"))
        assertTrue(rules.isPipelineFile("dockerfile.dev"))
        assertTrue(rules.isPipelineFile("docker-compose.yml"))
        assertTrue(rules.isPipelineFile("docker-compose.override.yaml"))
        assertTrue(rules.isPipelineFile("compose.yaml"))
        assertTrue(rules.isPipelineFile(".gitlab-ci.yml"))
        assertTrue(rules.isPipelineFile("Jenkinsfile"))
        assertTrue(rules.isPipelineFile("bitbucket-pipelines.yml"))
        assertTrue(rules.isPipelineFile(".travis.yml"))
        assertTrue(rules.isPipelineFile("azure-pipelines.yml"))
        assertTrue(rules.isPipelineFile("cloudbuild.yaml"))
        assertTrue(rules.isPipelineFile("buildkite.yml"))
    }

    @Test
    fun `isPipelineFile matches dockerfile and docker-compose prefixes`() {
        assertTrue(rules.isPipelineFile("Dockerfile.prod"))
        assertTrue(rules.isPipelineFile("docker-compose.dev.yml"))
        assertTrue(rules.isPipelineFile("compose.dev.yaml"))
    }

    @Test
    fun `isPipelineFile rejects unrelated names`() {
        assertFalse(rules.isPipelineFile("README.md"))
        assertFalse(rules.isPipelineFile("Foo.kt"))
        assertFalse(rules.isPipelineFile("application.properties"))
    }

    @Test
    fun `isPipelineFile understands repository-relative paths`() {
        assertTrue(rules.isPipelineFile(".github/workflows/ci.yml"))
        assertTrue(rules.isPipelineFile(".github/workflows/ci.yaml"))
        assertTrue(rules.isPipelineFile(".circleci/config.yml"))
        assertTrue(rules.isPipelineFile(".circleci/config.yaml"))
        assertTrue(rules.isPipelineFile("infra\\Dockerfile"))
        assertTrue(rules.isPipelineFile("buildkite.extra.yml"))
        assertTrue(rules.isPipelineFile("buildkite.extra.yaml"))
        assertTrue(rules.isPipelineFile("buildkite.yml.local"))
        assertFalse(rules.isPipelineFile(".github/workflows/ci.txt"))
        assertFalse(rules.isPipelineFile(".circleci/other.yml"))
        assertFalse(rules.isPipelineFile("docs/config.yml"))
        assertFalse(rules.isPipelineFile("buildkite.txt"))
    }

    @Test
    fun `isPipelineDirectory matches pipeline-specific directory names case-insensitively`() {
        assertTrue(rules.isPipelineDirectory(".github"))
        assertTrue(rules.isPipelineDirectory(".circleci"))
        assertTrue(rules.isPipelineDirectory(".devcontainer"))
        assertTrue(rules.isPipelineDirectory(".GITHUB"))
        assertFalse(rules.isPipelineDirectory("node_modules"))
        assertFalse(rules.isPipelineDirectory("src"))
    }

    @Test
    fun `isPriorityPipelineFile prioritises docker-compose and CI files`() {
        assertTrue(rules.isPriorityPipelineFile(File("docker-compose.yml")))
        assertTrue(rules.isPriorityPipelineFile(File("compose.yaml")))
        assertTrue(rules.isPriorityPipelineFile(File(".gitlab-ci.yml")))
        assertTrue(rules.isPriorityPipelineFile(File("Jenkinsfile")))
        assertFalse(rules.isPriorityPipelineFile(File("Dockerfile")))
    }

    @Test
    fun `isCodeFile matches all supported extensions`() {
        assertTrue(rules.isCodeFile("Foo.kt"))
        assertTrue(rules.isCodeFile("Bar.java"))
        assertTrue(rules.isCodeFile("script.py"))
        assertTrue(rules.isCodeFile("module.ts"))
        assertTrue(rules.isCodeFile("page.jsx"))
        assertTrue(rules.isCodeFile("lib.go"))
        assertTrue(rules.isCodeFile("schema.sql"))
        assertTrue(rules.isCodeFile("config.yaml"))
        assertTrue(rules.isCodeFile("build.gradle.kts"))
        assertTrue(rules.isCodeFile("README.md"))
        assertFalse(rules.isCodeFile("image.png"))
        assertFalse(rules.isCodeFile("archive.zip"))
        assertFalse(rules.isCodeFile("binary"))
    }

    @Test
    fun `isPriorityFile prioritises build files and entry-point files`() {
        assertTrue(rules.isPriorityFile("README.md"))
        assertTrue(rules.isPriorityFile("build.gradle.kts"))
        assertTrue(rules.isPriorityFile("settings.gradle"))
        assertTrue(rules.isPriorityFile("pom.xml"))
        assertTrue(rules.isPriorityFile("package.json"))
        assertTrue(rules.isPriorityFile("Cargo.toml"))
        assertTrue(rules.isPriorityFile("go.mod"))
        assertTrue(rules.isPriorityFile("docker-compose.yml"))
        assertTrue(rules.isPriorityFile("main.kt"))
        assertTrue(rules.isPriorityFile("index.ts"))
        assertTrue(rules.isPriorityFile("UserController.kt"))
        assertTrue(rules.isPriorityFile("AuthService.kt"))
        assertTrue(rules.isPriorityFile("LoginHandler.kt"))
    }

    @Test
    fun `isPriorityFile rejects non-priority files`() {
        assertFalse(rules.isPriorityFile("utils.kt"))
        assertFalse(rules.isPriorityFile("Constants.kt"))
        assertFalse(rules.isPriorityFile("image.png"))
        assertFalse(rules.isPriorityFile("archive.zip"))
    }

    @Test
    fun `default rules expose the documented defaults`() {
        val defaults = RepoScanRules()
        assertEquals(RepoScanRules.DEFAULT_PIPELINE_FILE_NAMES, defaults.pipelineFileNames)
        assertEquals(RepoScanRules.DEFAULT_CODE_EXTENSIONS, defaults.codeExtensions)
        assertEquals(RepoScanRules.DEFAULT_SKIP_DIRS, defaults.skipDirs)
        assertEquals(RepoScanRules.DEFAULT_PIPELINE_DIR_NAMES, defaults.pipelineDirectoryNames)
    }

    @Test
    fun `custom rules can override defaults`() {
        val custom =
            RepoScanRules(
                pipelineFileNames = setOf("custom-pipeline.yml"),
                codeExtensions = setOf("custom"),
                skipDirs = setOf("skipme"),
                pipelineDirectoryNames = setOf(".custom-ci"),
            )
        assertTrue(custom.isPipelineFile("custom-pipeline.yml"))
        assertTrue(custom.isCodeFile("file.custom"))
        assertTrue(custom.isPipelineDirectory(".custom-ci"))
        assertFalse(custom.isPipelineFile("README.md"))
        assertFalse(custom.isCodeFile("file.kt"))
        assertFalse(custom.isPipelineDirectory("node_modules"))
    }
}
