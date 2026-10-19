package com.stacklens.adapter.out.docker

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class DockerComposeAdapterComposeFileQueryTest {
    private val adapter = ComposeFileLocationAdapter("/tmp", DockerOsPaths())

    private fun composeWith(body: String): File =
        File.createTempFile("compose-", ".yml").apply {
            deleteOnExit()
            writeText(body)
        }

    @Test
    fun `findComposeContainerNames returns the declared names in order`(
        @TempDir dir: Path,
    ) {
        val compose =
            composeWith(
                """
                services:
                  a:
                    container_name: fake-atlassian
                  b:
                    image: nginx
                  c:
                    container_name:    fake-gitlab${"   "}
                """.trimIndent(),
            )

        assertEquals(listOf("fake-atlassian", "fake-gitlab"), adapter.findComposeContainerNames(compose.absolutePath))
    }

    @Test
    fun `findComposeContainerNames ignores blank values and files without matches`() {
        val compose =
            composeWith(
                """
                services:
                  a:
                    container_name:
                  b:
                    container_name: ${"\u0020\u0020"}
                  c:
                    image: nginx
                """.trimIndent(),
            )

        assertTrue(adapter.findComposeContainerNames(compose.absolutePath).isEmpty())
    }

    @Test
    fun `findComposeContainerNames is empty when the compose file cannot be read`() {
        val missing = File(dir(), "definitely-missing-${System.nanoTime()}.yml")

        assertTrue(adapter.findComposeContainerNames(missing.absolutePath).isEmpty())
    }

    @Test
    fun `resolveComposeSiblingFile returns an existing sibling`(
        @TempDir dir: Path,
    ) {
        val root = dir.toFile()
        val initDir = File(root, "demo-mysql/initdb").apply { mkdirs() }
        val sql = File(initDir, "01-schema.sql").apply { writeText("-- dummy") }
        val compose = File(root, "docker-compose.yml").apply { writeText("version: '3'") }

        assertEquals(sql.absolutePath, adapter.resolveComposeSiblingFile(compose.absolutePath, "demo-mysql/initdb/01-schema.sql"))
    }

    @Test
    fun `resolveComposeSiblingFile returns null for a missing sibling`(
        @TempDir dir: Path,
    ) {
        val compose = File(dir.toFile(), "docker-compose.yml").apply { writeText("version: '3'") }

        assertNull(adapter.resolveComposeSiblingFile(compose.absolutePath, "demo-mysql/initdb/01-schema.sql"))
    }

    @Test
    fun `resolveComposeSiblingFile ignores directories`(
        @TempDir dir: Path,
    ) {
        val compose = File(dir.toFile(), "docker-compose.yml").apply { writeText("version: '3'") }
        File(dir.toFile(), "sub").mkdirs()

        assertNull(adapter.resolveComposeSiblingFile(compose.absolutePath, "sub"))
    }

    private fun dir(): File = File(System.getProperty("java.io.tmpdir"))
}

class ComposeFileLocationPortCompositionTest {
    @Test
    fun `the compose port exposes compose-file queries to callers`(
        @TempDir dir: Path,
    ) {
        val port: com.stacklens.application.port.out.docker.ComposeFileLocationPort = ComposeFileLocationAdapter(dir.toString(), DockerOsPaths())
        val compose = File(dir.toFile(), "docker-compose.yml").apply { writeText("services:\n  x:\n    container_name: x\n") }

        assertEquals(listOf("x"), port.findComposeContainerNames(compose.absolutePath))
    }
}

class DirectoryNavigationTest {
    private val adapter = ComposeFileLocationAdapter(".", DockerOsPaths())

    @Test
    fun `a mounted path reports the directory that contains it`() {
        assertEquals("/opt/stacklens", adapter.containingDirectory("/opt/stacklens/docker-compose.yml"))
    }

    @Test
    fun `a path without a parent has no containing directory`() {
        assertEquals(null, adapter.containingDirectory("/"))
    }
}
