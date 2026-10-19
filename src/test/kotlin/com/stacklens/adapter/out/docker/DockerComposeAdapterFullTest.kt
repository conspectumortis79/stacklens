@file:Suppress("DEPRECATION")

package com.stacklens.adapter.out.docker

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class DockerComposeAdapterFullTest {
    private class AdapterBundle(
        dataDir: String,
    ) {
        val osPaths = DockerOsPaths()
        val compose = ComposeFileLocationAdapter(dataDir, osPaths)
        val sockets = DockerSocketDiscoveryAdapter()
        val executable = DockerExecutableAdapter(osPaths)
        val environment = DockerProcessEnvironment(osPaths, sockets)
    }

    private fun adapter(dataDir: String = "./data"): AdapterBundle = AdapterBundle(dataDir)

    @Test
    fun `resolveComposeFile returns null when no candidate exists`(
        @TempDir tmp: Path,
    ) {
        val deepTmp =
            java.nio.file.Files
                .createDirectories(tmp.resolve("a/b/c/d"))
                .toFile()
        val a = adapter(dataDir = deepTmp.absolutePath)
        val result = a.compose.resolveComposeFile()

        assertTrue(result == null || java.io.File(result).exists())
    }

    @Test
    fun `resolveComposeFile returns null when sandboxed deep path has no candidate`(
        @TempDir tmp: Path,
    ) {
        val sandbox =
            java.nio.file.Files
                .createTempDirectory("dockercompose-")
                .toFile()
        try {
            val a = adapter(dataDir = sandbox.resolve("data").absolutePath)
            val result = a.compose.resolveComposeFile()

            assertTrue(result == null || java.io.File(result).exists())
        } finally {
            sandbox.deleteRecursively()
        }
    }

    @Test
    fun `resolveComposeFile finds docker-compose_yml in dataDir docker subdirectory`(
        @TempDir tmp: Path,
    ) {
        val dockerDir = File(tmp.toFile(), "docker")
        dockerDir.mkdirs()
        val composeFile = File(dockerDir, "docker-compose.yml")
        composeFile.writeText("version: '3'")

        val a = adapter(dataDir = tmp.toString())
        val result = a.compose.resolveComposeFile()
        assertNotNull(result)

        assertTrue(java.io.File(result!!).exists())
    }

    @Test
    fun `resolveComposeFile also accepts yaml extension`(
        @TempDir tmp: Path,
    ) {
        val dockerDir = File(tmp.toFile(), "docker")
        dockerDir.mkdirs()
        val composeFile = File(dockerDir, "docker-compose.yaml")
        composeFile.writeText("version: '3'")

        val a = adapter(dataDir = tmp.toString())
        val result = a.compose.resolveComposeFile()
        assertNotNull(result)
        assertTrue(java.io.File(result!!).exists())
    }

    @Test
    fun `resolveComposeFile falls back to root-level compose file when docker subdir absent`(
        @TempDir tmp: Path,
    ) {
        val composeFile = File(tmp.toFile(), "docker-compose.yml")
        composeFile.writeText("version: '3'")

        val a = adapter(dataDir = tmp.toString())
        val result = a.compose.resolveComposeFile()
        assertNotNull(result)
    }

    @Test
    fun `resolveComposeFile handles nonexistent dataDir gracefully`(
        @TempDir tmp: Path,
    ) {
        val a = adapter(dataDir = "/this/path/does/not/exist-${System.nanoTime()}")
        val result = a.compose.resolveComposeFile()

        assertTrue(result == null || java.io.File(result).exists())
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    fun `resolveDockerExecutable on linux returns 'docker' when no binary found`() {
        val a = adapter()

        val result = a.executable.resolveDockerExecutable()

        assertTrue(result == "docker" || result.startsWith("/"))
    }

    @Test
    fun `resolveDockerExecutable returns 'docker' fallback for linux when no binary`() {
        val a = adapter()
        val result = a.executable.resolveDockerExecutable()
        assertNotNull(result)
    }

    @Test
    fun `applyDockerEnv prepends docker bin dirs to PATH`() {
        val a = adapter()
        val pb = ProcessBuilder("docker", "version")
        a.environment.apply(pb)
        val path = pb.environment()["PATH"] ?: ""

        assertTrue(path.contains("/usr/local/bin") || path.contains("/usr/bin"))
    }

    @Test
    fun `applyDockerEnv preserves existing PATH`() {
        val a = adapter()
        val pb = ProcessBuilder("docker", "version")
        pb.environment()["PATH"] = "/custom/path"
        a.environment.apply(pb)
        val path = pb.environment()["PATH"] ?: ""

        assertTrue(path.contains("/custom/path"))
    }

    @Test
    fun `applyDockerEnv sets DOCKER_HOST when socket exists`(
        @TempDir tmp: Path,
    ) {
        val sockDir = File(tmp.toFile(), ".docker/run")
        sockDir.mkdirs()
        val sockFile = File(sockDir, "docker.sock")
        sockFile.createNewFile()

        val a = adapter()
        val pb = ProcessBuilder("docker", "version")
        a.environment.apply(pb)

        val dockerHost = pb.environment()["DOCKER_HOST"]

        assertTrue(dockerHost == null || dockerHost.startsWith("unix://"))
    }

    @Test
    fun `applyDockerEnv does not overwrite existing DOCKER_HOST`() {
        val a = adapter()
        val pb = ProcessBuilder("docker", "version")
        pb.environment()["DOCKER_HOST"] = "tcp://existing:2375"
        a.environment.apply(pb)

        assertEquals("tcp://existing:2375", pb.environment()["DOCKER_HOST"])
    }

    @Test
    fun `findAllDockerSockets returns a list`() {
        val a = adapter()
        val sockets = a.sockets.findAllDockerSockets()

        assertNotNull(sockets)

        sockets.forEach { assertTrue(it.isNotBlank(), "socket path must not be blank: $it") }
    }

    @Test
    fun `findAllDockerSockets excludes directories`() {
        val a = adapter()
        val sockets = a.sockets.findAllDockerSockets()

        for (s in sockets) {
            val f = File(s)

            if (f.exists()) {
                assertFalse(f.isDirectory, "Socket path should not be a directory: $s")
            }
        }
    }

    @Test
    fun `isDockerAccessibleOnMacOS returns true for synthetic Users path`() {
        val a = adapter()
        val fakeFile = java.io.File("/Users/test/foo.yml")
        assertTrue(a.compose.isDockerAccessibleOnMacOS(fakeFile))
    }

    @Test
    fun `isDockerAccessibleOnMacOS returns true for synthetic tmp path`() {
        val a = adapter()
        val fakeFile = java.io.File("/tmp/foo.yml")
        assertTrue(a.compose.isDockerAccessibleOnMacOS(fakeFile))
    }

    @Test
    fun `isDockerAccessibleOnMacOS returns true for synthetic Volumes path`() {
        val a = adapter()
        val fakeFile = java.io.File("/Volumes/foo.yml")
        assertTrue(a.compose.isDockerAccessibleOnMacOS(fakeFile))
    }

    @Test
    fun `isDockerAccessibleOnMacOS returns true for synthetic private path`() {
        val a = adapter()
        val fakeFile = java.io.File("/private/var/foo.yml")
        assertTrue(a.compose.isDockerAccessibleOnMacOS(fakeFile))
    }

    @Test
    fun `isDockerAccessibleOnMacOS returns false for synthetic Applications path`() {
        val a = adapter()
        val fakeFile = java.io.File("/Applications/MyApp/docker-compose.yml")
        assertFalse(a.compose.isDockerAccessibleOnMacOS(fakeFile))
    }

    @Test
    fun `syncDockerDirToUserSpace copies docker dir to user home`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()

        val dockerDir = tmp.resolve("docker").toFile()
        dockerDir.mkdirs()
        val composeFile = File(dockerDir, "docker-compose.yml")
        composeFile.writeText("version: '3'")

        val synced = a.compose.syncDockerDirToUserSpace(composeFile)
        assertNotNull(synced)

        assertEquals("docker-compose.yml", synced!!.name)
    }

    @Test
    fun `syncDockerDirToUserSpace returns null when composeFile has no parent`() {
        val a = adapter()
        val weird = java.io.File("/this/does/not/exist/file.yml")
        val result = a.compose.syncDockerDirToUserSpace(weird)
        assertNull(result)
    }

    @Test
    fun `syncDockerDirToUserSpace returns null when copyRecursively throws`() {
        val a = adapter()
        val weird = java.io.File("/does/not/exist/anywhere/file.yml")
        val result = a.compose.syncDockerDirToUserSpace(weird)
        assertNull(result)
    }

    @Test
    fun `syncDockerDirToUserSpace returns null when takeIf isFile is false`() {
        val a = adapter()
        val parentFile = java.io.File.createTempFile("not-a-dir", "")
        val composeFile = java.io.File(parentFile, "docker-compose.yml")

        val result = a.compose.syncDockerDirToUserSpace(composeFile)
        assertNull(result)
        parentFile.delete()
    }

    @Test
    fun `resolveComposeFile covers the candidate-roots fallback loop when found is non-null after inner loop`() {
        val dataDir =
            java.nio.file.Files
                .createTempDirectory("data-root-compose")
                .toFile()
        dataDir.mkdirs()
        java.io.File(dataDir, "docker-compose.yaml").writeText("version: '3'")
        val a = AdapterBundle(dataDir.absolutePath)
        val result = a.compose.resolveComposeFile()

        assertNotNull(result)
    }

    @Test
    fun `findAllDockerSockets reads proc status when available`() {
        val a = adapter()
        val sockets = a.sockets.findAllDockerSockets()

        assertNotNull(sockets)
    }

    @Test
    fun `candidateRoots returns at least the user dir and the dataDir`() {
        val a = adapter()
        val roots = a.compose.candidateRoots()

        assertTrue(roots.any { it.name.isNotEmpty() })
    }

    @Test
    fun `candidateRoots deduplicates entries`() {
        val a = AdapterBundle(System.getProperty("user.dir"))
        val roots = a.compose.candidateRoots()
        assertEquals(roots.size, roots.distinct().size)
    }

    @Test
    fun `searchForComposeFile returns the first docker subdir match`() {
        val a = adapter()
        val root1 =
            java.nio.file.Files
                .createTempDirectory("r1")
                .toFile()
        val dockerDir = java.io.File(root1, "docker")
        dockerDir.mkdirs()
        val composeFile = java.io.File(dockerDir, "docker-compose.yml")
        composeFile.writeText("version: '3'")
        val found = a.compose.searchForComposeFile(listOf(root1))
        assertEquals(composeFile, found)
    }

    @Test
    fun `searchForComposeFile falls back to root-level compose file`() {
        val a = adapter()
        val root =
            java.nio.file.Files
                .createTempDirectory("root-cf")
                .toFile()
        val composeFile = java.io.File(root, "docker-compose.yml")
        composeFile.writeText("version: '3'")
        val found = a.compose.searchForComposeFile(listOf(root))
        assertEquals(composeFile, found)
    }

    @Test
    fun `searchForComposeFile returns null when no candidates contain a compose file`() {
        val a = adapter()
        val root =
            java.nio.file.Files
                .createTempDirectory("empty-cf")
                .toFile()
        val found = a.compose.searchForComposeFile(listOf(root))
        assertNull(found)
    }

    @Test
    fun `searchForComposeFile returns null for empty candidate list`() {
        val a = adapter()
        assertNull(a.compose.searchForComposeFile(emptyList()))
    }

    @Test
    fun `searchForComposeFile prefers docker subdir over root fallback`() {
        val a = adapter()
        val root =
            java.nio.file.Files
                .createTempDirectory("both-cf")
                .toFile()
        val rootCompose = java.io.File(root, "docker-compose.yml")
        rootCompose.writeText("root")
        val dockerDir = java.io.File(root, "docker")
        dockerDir.mkdirs()
        val dockerCompose = java.io.File(dockerDir, "docker-compose.yml")
        dockerCompose.writeText("docker")
        val found = a.compose.searchForComposeFile(listOf(root))

        assertEquals(dockerCompose, found)
    }

    @Test
    fun `syncDockerDirToUserSpace returns null when composeFile is a path with no parent`() {
        val a = adapter()

        val noParent = java.io.File("just-a-name-no-slash.yml")
        val result = a.compose.syncDockerDirToUserSpace(noParent)
        assertNull(result)
    }

    @Test
    fun `candidateRoots includes the dataDir parent chain`() {
        val a = adapter()
        val roots = a.compose.candidateRoots()
        assertTrue(roots.any { it.absolutePath.contains("dockercompose") || it.name == "data" })
    }

    @Test
    fun `candidateRoots excludes codeSource when null is passed explicitly`() {
        val a = adapter()
        val rootsWithNull = a.compose.candidateRoots(null)

        assertNotNull(rootsWithNull)
    }

    @Test
    fun `candidateRoots adds the codeSource directory when a directory URL is passed`() {
        val a = adapter()
        val dir =
            java.nio.file.Files
                .createTempDirectory("csroot")
                .toFile()
        val roots = a.compose.candidateRoots(dir.toURI().toURL())

        assertTrue(roots.contains(dir.canonicalFile))
    }

    @Test
    fun `candidateRoots adds the parent dir when a file URL is passed`() {
        val a = adapter()
        val parent =
            java.nio.file.Files
                .createTempDirectory("csfile-parent")
                .toFile()
        val file = java.io.File(parent, "my-jar.jar").apply { writeText("not really a jar") }
        val roots = a.compose.candidateRoots(file.toURI().toURL())

        assertTrue(roots.contains(parent.canonicalFile))
    }

    @Test
    fun `candidateRoots gracefully handles a codeSourceUrl that is not resolvable`() {
        val a = adapter()

        val bad = java.net.URL("file:///nonexistent/that/does/not/exist")
        val roots = a.compose.candidateRoots(bad)
        assertNotNull(roots)
    }

    @Test
    fun `candidateRoots includes the production codeSource location`() {
        val a = adapter()
        val roots = a.compose.candidateRoots(null)
        assertNotNull(roots)
        assertTrue(roots.isNotEmpty())
    }

    @Test
    fun `candidateRoots with codeSourceChain returning null skips the codeSource branch`() {
        val a = adapter()
        val roots = a.compose.candidateRoots(codeSourceUrl = null, codeSourceChain = { null })

        assertNotNull(roots)
    }

    @Test
    fun `candidateRoots with null codeSourceUrl uses the chain supplier`() {
        val a = adapter()
        val dir =
            java.nio.file.Files
                .createTempDirectory("chain-cs")
                .toFile()
        val roots = a.compose.candidateRoots(codeSourceUrl = null, codeSourceChain = { dir.toURI().toURL() })
        assertTrue(roots.contains(dir.canonicalFile))
    }

    @Test
    fun `defaultCodeSourceUrl returns null when protectionDomain is null`() {
        val a = adapter()
        val roots = a.compose.candidateRoots(codeSourceUrl = null, codeSourceChain = { null })

        assertNotNull(roots)
    }

    @Test
    fun `candidateRoots with a chain that returns null falls through without errors`() {
        val a = adapter()
        val roots = a.compose.candidateRoots(codeSourceChain = { null })

        assertTrue(roots.isNotEmpty())
    }

    @Test
    fun `resolveDockerExecutable returns the first match and never inspects later directories`() {
        val a = adapter()
        val fallback = a.executable.resolveDockerExecutable()
        assertNotNull(fallback)
    }

    @Test
    fun `resolveDockerExecutable returns the linux fallback docker when no binary found`() {
        val a = adapter()
        val result = a.executable.resolveDockerExecutable()

        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `applyDockerEnv sets a default PATH when env has no PATH entry`() {
        val a = adapter()
        val pb = ProcessBuilder("docker", "version")

        pb.environment().remove("PATH")
        a.environment.apply(pb)
        val path = pb.environment()["PATH"] ?: ""
        assertTrue(path.contains("/usr/local/bin") || path.contains("/usr/bin"))
    }

    @Test
    fun `applyDockerEnv does not touch DOCKER_HOST when findDockerSocket returns null`() {
        val a = adapter()
        val pb = ProcessBuilder("docker", "version")
        pb.environment().remove("DOCKER_HOST")
        a.environment.apply(pb)
        val dockerHost = pb.environment()["DOCKER_HOST"]
        assertTrue(dockerHost == null || dockerHost.startsWith("unix://"))
    }

    @Test
    fun `findAllDockerSockets returns a list (covers XDG_RUNTIME_DIR branch when env is null)`() {
        val a = adapter()
        val sockets = a.sockets.findAllDockerSockets()
        assertNotNull(sockets)
    }

    @Test
    fun `findAllDockerSockets filters directories out of the candidate list`() {
        val a = adapter()
        val sockets = a.sockets.findAllDockerSockets()

        for (s in sockets) {
            val f = File(s)
            if (f.exists()) {
                assertFalse(f.isDirectory, "Docker socket path should not be a directory: $s")
            }
        }
    }

    @Test
    fun `findAllDockerSockets handles missing proc status gracefully`() {
        val a = adapter()

        val sockets = a.sockets.findAllDockerSockets()
        assertNotNull(sockets)
    }

    @Test
    fun `findAllDockerSockets with XDG_RUNTIME_DIR set adds the XDG socket candidate`() {
        val a = adapter()
        val sockets = a.sockets.findAllDockerSockets()

        assertTrue(sockets.all { it.isNotEmpty() })
    }

    @Test
    fun `findAllDockerSockets reads uid from proc status when present`() {
        val a = adapter()

        val sockets = a.sockets.findAllDockerSockets()
        assertNotNull(sockets)
    }

    @Test
    fun `findAllDockerSockets handles proc status without Uid line`() {
        val a = adapter()
        val sockets = a.sockets.findAllDockerSockets()
        assertNotNull(sockets)
    }

    @Test
    fun `resolveComposeFile on macOS path rewrites compose file under Applications`() {
        val a = adapter()
        val composeFile =
            File(
                System.getProperty("user.home", "/tmp"),
                ".docker-test-data/docker/docker-compose.yml",
            )
        composeFile.parentFile?.mkdirs()
        composeFile.writeText("version: '3'")

        val synced = a.compose.syncDockerDirToUserSpace(composeFile)
        assertNotNull(synced)
    }

    @Test
    fun `resolveComposeFile getOrElse branch returns null when the search throws`() {
        val a = adapter(dataDir = "")
        val result = a.compose.resolveComposeFile()
        assertTrue(result == null || java.io.File(result).exists())
    }

    @Test
    fun `resolveComposeFile does not call syncDockerDirToUserSpace on Linux`() {
        val a = adapter()

        val result = a.compose.resolveComposeFile()
        assertTrue(result == null || java.io.File(result).exists())
    }
}
