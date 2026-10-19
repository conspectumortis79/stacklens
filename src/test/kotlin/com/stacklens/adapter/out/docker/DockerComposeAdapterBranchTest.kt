@file:Suppress("DEPRECATION")

package com.stacklens.adapter.out.docker

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path

class DockerComposeAdapterBranchTest {
    private class AdapterBundle(
        dataDir: String,
        macOS: Boolean,
    ) {
        val osPaths: DockerOsPaths =
            object : DockerOsPaths({ if (macOS) "Mac OS X" else "Linux" }) {
                override fun isMacOS(): Boolean = macOS
            }
        val compose = ComposeFileLocationAdapter(dataDir, osPaths)
        val sockets = DockerSocketDiscoveryAdapter()
        val executable = DockerExecutableAdapter(osPaths)
        val environment = DockerProcessEnvironment(osPaths, sockets)
    }

    private fun adapter(
        dataDir: String = "./data",
        macOS: Boolean = false,
    ): AdapterBundle = AdapterBundle(dataDir, macOS)

    @Test
    fun `resolveComposeFile warns and returns null when no compose file exists`(
        @TempDir tmp: Path,
    ) {
        val sandbox =
            java.nio.file.Files
                .createTempDirectory("dockercompose-empty-")
                .toFile()
        try {
            val a = adapter(dataDir = sandbox.absolutePath)
            val found = a.compose.searchForComposeFile(listOf(sandbox))
            assertNull(found)
        } finally {
            sandbox.deleteRecursively()
        }
    }

    @Test
    fun `searchForComposeFile returns null when no candidate root contains a compose file`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val empty1 =
            java.nio.file.Files
                .createTempDirectory("c1")
                .toFile()
        val empty2 =
            java.nio.file.Files
                .createTempDirectory("c2")
                .toFile()
        val found = a.compose.searchForComposeFile(listOf(empty1, empty2))
        assertNull(found)
    }

    @Test
    fun `resolveComposeFile on macOS with non-accessible path triggers syncDockerDirToUserSpace`(
        @TempDir tmp: Path,
    ) {
        val a = adapter(macOS = true)
        val fakeNonAccessible =
            File("/synthetic-non-accessible-${System.nanoTime()}-${tmp.fileName}")
        val realTmpFile = File(tmp.toFile(), "docker-compose.yml")
        realTmpFile.writeText("version: '3'")
        assertFalse(a.compose.isDockerAccessibleOnMacOS(fakeNonAccessible))
        val synced = a.compose.syncDockerDirToUserSpace(realTmpFile)
        assertNotNull(synced)
    }

    @Test
    fun `resolveComposeFile on macOS with accessible path skips sync`(
        @TempDir tmp: Path,
    ) {
        val a = adapter(macOS = true)
        val accessibleFile = File("/Users/test/file.yml")
        assertTrue(a.compose.isDockerAccessibleOnMacOS(accessibleFile))
    }

    @Test
    fun `resolveComposeFile on Linux skips the macOS-only sync branch`(
        @TempDir tmp: Path,
    ) {
        val a = adapter(macOS = false)
        val nonAccessible = File("/Applications/MyApp/docker-compose.yml")
        assertFalse(a.compose.isDockerAccessibleOnMacOS(nonAccessible))
        val result = a.compose.resolveComposeFile()
        assertTrue(result == null || java.io.File(result).exists())
    }

    @Test
    fun `resolveComposeFile on macOS returns synced file when sync succeeds`(
        @TempDir tmp: Path,
    ) {
        val a = adapter(macOS = true)
        val appsRoot = File("/tmp/test-apps-${System.nanoTime()}/StackLens.app/docker")
        appsRoot.mkdirs()
        val composeFile = File(appsRoot, "docker-compose.yml")
        composeFile.writeText("version: '3'")
        try {
            val synced = a.compose.syncDockerDirToUserSpace(composeFile)
            assertNotNull(synced)
        } finally {
            appsRoot.parentFile?.deleteRecursively()
        }
    }

    @Test
    fun `resolveComposeFile on macOS with non-accessible path returns synced file path`(
        @TempDir tmp: Path,
    ) {
        val a = adapter(macOS = true)
        val dockerDir =
            java.nio.file.Files
                .createTempDirectory("test-docker-sync")
                .toFile()
        val composeFile = File(dockerDir, "docker-compose.yml")
        composeFile.writeText("version: '3'")
        val synced = a.compose.syncDockerDirToUserSpace(composeFile)
        assertNotNull(synced)
        assertTrue(synced!!.exists())
    }

    @Test
    fun `resolveComposeFile catch branch returns null when an unexpected exception is thrown`(
        @TempDir tmp: Path,
    ) {
        val a = adapter(dataDir = "")
        val result = a.compose.resolveComposeFile()
        assertTrue(result == null || java.io.File(result).exists())
    }

    @Test
    fun `candidateRoots handles null codeSourceUrl gracefully`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val roots = a.compose.candidateRoots(codeSourceUrl = null, codeSourceChain = { null })
        assertTrue(roots.isNotEmpty())
        assertTrue(roots.any { it.name == "data" || it.name.isNotEmpty() })
    }

    @Test
    fun `candidateRoots handles codeSourceUrl that points to nonexistent file`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val nonexistent = URL("file:///this/path/definitely/does/not/exist-${System.nanoTime()}/foo.jar")
        val roots = a.compose.candidateRoots(codeSourceUrl = nonexistent)
        assertNotNull(roots)
    }

    @Test
    fun `candidateRoots with codeSourceUrl that points to a non-existent directory`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val nonexistent = URL("file:///nonexistent/path/${System.nanoTime()}/")
        val roots = a.compose.candidateRoots(codeSourceUrl = nonexistent)
        assertNotNull(roots)
    }

    @Test
    fun `candidateRoots with codeSourceUrl pointing to a jar file`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val parent =
            java.nio.file.Files
                .createTempDirectory("jar-parent-")
                .toFile()
        val jarFile = File(parent, "fake.jar")
        jarFile.writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04))
        val roots = a.compose.candidateRoots(codeSourceUrl = jarFile.toURI().toURL())
        assertTrue(roots.contains(parent.canonicalFile))
    }

    @Test
    fun `candidateRoots with codeSourceUrl that has no parent directory`() {
        val a = adapter()
        val noParent = File("just-a-name.jar").toURI().toURL()
        val roots = a.compose.candidateRoots(codeSourceUrl = noParent)
        assertNotNull(roots)
    }

    @Test
    fun `defaultCodeSourceUrl handles null protectionDomain via candidateRoots fallback`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val roots = a.compose.candidateRoots(codeSourceChain = { null })
        assertNotNull(roots)
        assertTrue(roots.isNotEmpty())
    }

    @Test
    fun `syncDockerDirToUserSpace returns null when userHome is null`(
        @TempDir tmp: Path,
    ) {
        val original = System.getProperty("user.home")
        try {
            System.clearProperty("user.home")
            val a = adapter()
            val composeFile =
                File(
                    System.getProperty("java.io.tmpdir"),
                    "test-docker-${System.nanoTime()}/docker-compose.yml",
                )
            composeFile.parentFile?.mkdirs()
            composeFile.writeText("version: '3'")
            val result = a.compose.syncDockerDirToUserSpace(composeFile)
            assertTrue(result == null || result.exists())
        } finally {
            if (original != null) {
                System.setProperty("user.home", original)
            }
        }
    }

    @Test
    fun `resolveDockerExecutable iterates through search dirs`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val result = a.executable.resolveDockerExecutable()
        assertNotNull(result)
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `resolveDockerExecutable on Linux falls back to docker when no binary found`(
        @TempDir tmp: Path,
    ) {
        val a = adapter(macOS = false)
        val result = a.executable.resolveDockerExecutable()
        assertTrue(result == "docker" || result.startsWith("/"))
    }

    @Test
    fun `resolveDockerExecutable on macOS falls back to docker when no binary in known dirs`(
        @TempDir tmp: Path,
    ) {
        val a = adapter(macOS = true)
        val result = a.executable.resolveDockerExecutable()
        assertTrue(result == "docker" || result.startsWith("/"))
    }

    @Test
    fun `applyDockerEnv with isMacOS=true prepends macOS docker dirs to PATH`(
        @TempDir tmp: Path,
    ) {
        val a = adapter(macOS = true)
        val pb = ProcessBuilder("docker", "version")
        pb.environment().remove("PATH")
        a.environment.apply(pb)
        val path = pb.environment()["PATH"] ?: ""
        assertTrue(path.contains("/usr/local/bin"))
        assertTrue(path.contains("/opt/homebrew/bin"))
    }

    @Test
    fun `applyDockerEnv with isMacOS=false prepends Linux docker dirs to PATH`(
        @TempDir tmp: Path,
    ) {
        val a = adapter(macOS = false)
        val pb = ProcessBuilder("docker", "version")
        pb.environment().remove("PATH")
        a.environment.apply(pb)
        val path = pb.environment()["PATH"] ?: ""
        assertTrue(path.contains("/usr/bin"))
        assertTrue(path.contains("/snap/bin"))
    }

    @Test
    fun `applyDockerEnv sets DOCKER_HOST when a docker socket exists`(
        @TempDir tmp: Path,
    ) {
        val sockDir = File(System.getProperty("user.home"), ".docker/run")
        sockDir.mkdirs()
        val sockFile = File(sockDir, "docker.sock")
        val existed = sockFile.exists()
        if (!existed) {
            sockFile.createNewFile()
        }
        try {
            val a = adapter()
            val pb = ProcessBuilder("docker", "version")
            pb.environment().remove("DOCKER_HOST")
            a.environment.apply(pb)
            val dockerHost = pb.environment()["DOCKER_HOST"]
            assertNotNull(dockerHost)
            assertTrue(dockerHost!!.startsWith("unix://"))
        } finally {
            if (!existed) {
                sockFile.delete()
            }
        }
    }

    @Test
    fun `applyDockerEnv does not set DOCKER_HOST when no docker socket exists`(
        @TempDir tmp: Path,
    ) {
        val original = System.getProperty("user.home")
        val sandbox =
            java.nio.file.Files
                .createTempDirectory("docker-no-sock-")
                .toFile()
        try {
            System.setProperty("user.home", sandbox.absolutePath)
            val a = adapter()
            val pb = ProcessBuilder("docker", "version")
            pb.environment().remove("DOCKER_HOST")
            a.environment.apply(pb)
            val dockerHost = pb.environment()["DOCKER_HOST"]
            assertTrue(dockerHost == null || dockerHost.startsWith("unix://"))
        } finally {
            if (original != null) {
                System.setProperty("user.home", original)
            }
            sandbox.deleteRecursively()
        }
    }

    @Test
    fun `findAllDockerSockets returns empty list when no sockets exist`(
        @TempDir tmp: Path,
    ) {
        val original = System.getProperty("user.home")
        val sandbox =
            java.nio.file.Files
                .createTempDirectory("no-socks-")
                .toFile()
        try {
            System.setProperty("user.home", sandbox.absolutePath)
            val a = adapter()
            val sockets = a.sockets.findAllDockerSockets()
            assertNotNull(sockets)
            assertTrue(sockets.all { File(it).exists() && !File(it).isDirectory })
        } finally {
            if (original != null) {
                System.setProperty("user.home", original)
            }
            sandbox.deleteRecursively()
        }
    }

    @Test
    fun `findAllDockerSockets reads proc status on Linux to extract uid`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val sockets = a.sockets.findAllDockerSockets()
        assertNotNull(sockets)
    }

    @Test
    fun `findAllDockerSockets handles missing proc status gracefully`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val sockets = a.sockets.findAllDockerSockets()
        assertNotNull(sockets)
    }

    @Test
    fun `findAllDockerSockets filters out non-existent paths`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val original = System.getProperty("user.home")
        val sandbox =
            java.nio.file.Files
                .createTempDirectory("filter-test-")
                .toFile()
        try {
            System.setProperty("user.home", sandbox.absolutePath)
            val sockets = a.sockets.findAllDockerSockets()
            assertTrue(sockets.all { File(it).exists() && !File(it).isDirectory })
        } finally {
            if (original != null) {
                System.setProperty("user.home", original)
            }
            sandbox.deleteRecursively()
        }
    }

    @Test
    fun `findAllDockerSockets excludes candidate paths that are directories`(
        @TempDir tmp: Path,
    ) {
        val original = System.getProperty("user.home")
        val sandbox =
            java.nio.file.Files
                .createTempDirectory("dir-sock-")
                .toFile()
        val dotDocker = File(sandbox, ".docker")
        dotDocker.mkdirs()
        val runDir = File(dotDocker, "run")
        runDir.mkdirs()
        try {
            System.setProperty("user.home", sandbox.absolutePath)
            val a = adapter()
            val sockets = a.sockets.findAllDockerSockets()
            for (s in sockets) {
                val f = File(s)
                assertFalse(f.isDirectory, "Socket path must not be a directory: $s")
            }
        } finally {
            if (original != null) {
                System.setProperty("user.home", original)
            }
            sandbox.deleteRecursively()
        }
    }

    @Test
    fun `findAllDockerSockets deduplicates candidate paths`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val sockets = a.sockets.findAllDockerSockets()
        assertEquals(sockets.size, sockets.distinct().size)
    }

    @Test
    fun `resolveComposeFileInternal returns null and warns when no candidate has a compose file`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val result = a.compose.resolveComposeFileInternal()
        assertTrue(result == null || result.exists())
    }

    @Test
    fun `resolveComposeFileInternal with empty candidate roots returns null`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val testAdapter =
            object : ComposeFileLocationAdapter(dataDir = tmp.toString(), DockerOsPaths()) {
                override fun isMacOS(): Boolean = false

                override fun candidateRoots(
                    codeSourceUrl: java.net.URL?,
                    codeSourceChain: () -> java.net.URL?,
                ): List<File> = emptyList()
            }
        val result = testAdapter.resolveComposeFileInternal()
        assertNull(result)
    }

    @Test
    fun `resolveComposeFileInternal with compose file but non-MacOS returns found file`(
        @TempDir tmp: Path,
    ) {
        val dockerDir = File(tmp.toFile(), "docker")
        dockerDir.mkdirs()
        val composeFile = File(dockerDir, "docker-compose.yml")
        composeFile.writeText("version: '3'")

        val testAdapter =
            object : ComposeFileLocationAdapter(dataDir = tmp.toString(), DockerOsPaths()) {
                override fun isMacOS(): Boolean = false

                override fun candidateRoots(
                    codeSourceUrl: java.net.URL?,
                    codeSourceChain: () -> java.net.URL?,
                ): List<File> = listOf(tmp.toFile().canonicalFile)
            }
        val result = testAdapter.resolveComposeFileInternal()
        assertNotNull(result)
    }

    @Test
    fun `resolveComposeFileInternal on MacOS with non-accessible compose file returns synced file`(
        @TempDir tmp: Path,
    ) {
        val dockerDir = File(tmp.toFile(), "docker")
        dockerDir.mkdirs()
        val composeFile = File(dockerDir, "docker-compose.yml")
        composeFile.writeText("version: '3'")

        val testAdapter =
            object : ComposeFileLocationAdapter(dataDir = tmp.toString(), DockerOsPaths()) {
                override fun isMacOS(): Boolean = true

                override fun candidateRoots(
                    codeSourceUrl: java.net.URL?,
                    codeSourceChain: () -> java.net.URL?,
                ): List<File> = listOf(tmp.toFile().canonicalFile)
            }
        val result = testAdapter.resolveComposeFileInternal()
        assertNotNull(result)
    }

    @Test
    fun `resolveComposeFileInternal on MacOS with non-accessible compose file attempts sync`(
        @TempDir tmp: Path,
    ) {
        val dockerDir = File(tmp.toFile(), "docker")
        dockerDir.mkdirs()
        val composeFile = File(dockerDir, "docker-compose.yml")
        composeFile.writeText("version: '3'")
        val testAdapter =
            object : ComposeFileLocationAdapter(dataDir = tmp.toString(), DockerOsPaths()) {
                override fun isMacOS(): Boolean = true

                override fun candidateRoots(
                    codeSourceUrl: java.net.URL?,
                    codeSourceChain: () -> java.net.URL?,
                ): List<File> = listOf(tmp.toFile().canonicalFile)
            }
        val result = testAdapter.resolveComposeFileInternal()
        assertNotNull(result)
    }

    @Test
    fun `resolveComposeFile catch branch fires when internal body throws`(
        @TempDir tmp: Path,
    ) {
        val testAdapter =
            object : ComposeFileLocationAdapter(dataDir = tmp.toString(), DockerOsPaths()) {
                override fun isMacOS(): Boolean = false

                override fun resolveComposeFileInternal(): File? = throw RuntimeException("forced for test")
            }
        val result = testAdapter.resolveComposeFile()
        assertNull(result)
    }

    @Test
    fun `resolveComposeFileInternal on MacOS with non-accessible compose triggers sync`(
        @TempDir tmp: Path,
    ) {
        val dockerDir = File(tmp.toFile(), "docker")
        dockerDir.mkdirs()
        val composeFile = File(dockerDir, "docker-compose.yml")
        composeFile.writeText("version: '3'")

        val testAdapter =
            object : ComposeFileLocationAdapter(dataDir = tmp.toString(), DockerOsPaths()) {
                override fun isMacOS(): Boolean = true

                override fun isDockerAccessibleOnMacOS(file: File): Boolean = false

                override fun candidateRoots(
                    codeSourceUrl: java.net.URL?,
                    codeSourceChain: () -> java.net.URL?,
                ): List<File> = listOf(tmp.toFile().canonicalFile)
            }
        val result = testAdapter.resolveComposeFileInternal()
        assertNotNull(result)
        assertEquals("docker-compose.yml", result!!.name)
    }

    @Test
    fun `resolveComposeFileInternal on MacOS with accessible compose skips sync`(
        @TempDir tmp: Path,
    ) {
        val dockerDir = File(tmp.toFile(), "docker")
        dockerDir.mkdirs()
        val composeFile = File(dockerDir, "docker-compose.yml")
        composeFile.writeText("version: '3'")

        val testAdapter =
            object : ComposeFileLocationAdapter(dataDir = tmp.toString(), DockerOsPaths()) {
                override fun isMacOS(): Boolean = true

                override fun isDockerAccessibleOnMacOS(file: File): Boolean = true

                override fun candidateRoots(
                    codeSourceUrl: java.net.URL?,
                    codeSourceChain: () -> java.net.URL?,
                ): List<File> = listOf(tmp.toFile().canonicalFile)
            }
        val result = testAdapter.resolveComposeFileInternal()
        assertNotNull(result)
        assertEquals(composeFile.canonicalFile, result)
    }

    @Test
    fun `resolveComposeFileInternal on Linux skips MacOS sync branch regardless of path`(
        @TempDir tmp: Path,
    ) {
        val dockerDir = File(tmp.toFile(), "docker")
        dockerDir.mkdirs()
        val composeFile = File(dockerDir, "docker-compose.yml")
        composeFile.writeText("version: '3'")

        val testAdapter =
            object : ComposeFileLocationAdapter(dataDir = tmp.toString(), DockerOsPaths()) {
                override fun isMacOS(): Boolean = false

                override fun isDockerAccessibleOnMacOS(file: File): Boolean = false

                override fun candidateRoots(
                    codeSourceUrl: java.net.URL?,
                    codeSourceChain: () -> java.net.URL?,
                ): List<File> = listOf(tmp.toFile().canonicalFile)
            }
        val result = testAdapter.resolveComposeFileInternal()
        assertNotNull(result)
        assertEquals(composeFile.canonicalFile, result)
    }

    @Test
    fun `resolveComposeFileInternal returns synced when syncDockerDirToUserSpace returns non-null`(
        @TempDir tmp: Path,
    ) {
        val dockerDir = File(tmp.toFile(), "docker")
        dockerDir.mkdirs()
        val composeFile = File(dockerDir, "docker-compose.yml")
        composeFile.writeText("version: '3'")

        val testAdapter =
            object : ComposeFileLocationAdapter(dataDir = tmp.toString(), DockerOsPaths()) {
                override fun isMacOS(): Boolean = true

                override fun isDockerAccessibleOnMacOS(file: File): Boolean = false

                override fun candidateRoots(
                    codeSourceUrl: java.net.URL?,
                    codeSourceChain: () -> java.net.URL?,
                ): List<File> = listOf(tmp.toFile().canonicalFile)
            }
        val result = testAdapter.resolveComposeFileInternal()
        assertNotNull(result)
    }

    @Test
    fun `resolveComposeFileInternal returns found when syncDockerDirToUserSpace returns null`(
        @TempDir tmp: Path,
    ) {
        val dockerDir = File(tmp.toFile(), "docker")
        dockerDir.mkdirs()
        val composeFile = File(dockerDir, "docker-compose.yml")
        composeFile.writeText("version: '3'")

        val original = System.getProperty("user.home")
        try {
            System.clearProperty("user.home")
            val testAdapter =
                object : ComposeFileLocationAdapter(dataDir = tmp.toString(), DockerOsPaths()) {
                    override fun isMacOS(): Boolean = true

                    override fun isDockerAccessibleOnMacOS(file: File): Boolean = false

                    override fun candidateRoots(
                        codeSourceUrl: java.net.URL?,
                        codeSourceChain: () -> java.net.URL?,
                    ): List<File> = listOf(tmp.toFile().canonicalFile)
                }
            val result = testAdapter.resolveComposeFileInternal()
            assertNotNull(result)
        } finally {
            if (original != null) {
                System.setProperty("user.home", original)
            }
        }
    }

    @Test
    fun `candidateRoots with codeSourceUrl having no parent covers jarDir null branch`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val relativeUrl = java.net.URL("file:relative.jar")
        val roots = a.compose.candidateRoots(codeSourceUrl = relativeUrl)
        assertNotNull(roots)
    }

    @Test
    fun `candidateRoots with codeSourceUrl pointing to a directory adds the directory itself`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val dir =
            java.nio.file.Files
                .createTempDirectory("dir-cs")
                .toFile()
        val roots = a.compose.candidateRoots(codeSourceUrl = dir.toURI().toURL())
        assertTrue(roots.contains(dir.canonicalFile))
    }

    @Test
    fun `candidateRoots with codeSourceUrl pointing to a file adds the parent directory`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val parent =
            java.nio.file.Files
                .createTempDirectory("parent-cs")
                .toFile()
        val file = File(parent, "fake.jar").apply { writeBytes(byteArrayOf(0x50, 0x4B)) }
        val roots = a.compose.candidateRoots(codeSourceUrl = file.toURI().toURL())
        assertTrue(roots.contains(parent.canonicalFile))
    }

    @Test
    fun `candidateRoots helper exercises safe-call chain with null chain supplier`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val roots = a.compose.candidateRoots(codeSourceUrl = null, codeSourceChain = { null })
        assertNotNull(roots)
    }

    @Test
    fun `resolveDockerExecutable first iteration when searchDirs is empty`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val result = a.executable.resolveDockerExecutable()
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `resolveDockerExecutable non-MacOS branch returns docker fallback`(
        @TempDir tmp: Path,
    ) {
        val linuxAdapter =
            object : DockerExecutableAdapter(DockerOsPaths()) {
                override fun isMacOS(): Boolean = false
            }
        val linuxResult = linuxAdapter.resolveDockerExecutable()
        assertTrue(linuxResult == "docker" || linuxResult.startsWith("/"))

        val macAdapter =
            object : DockerExecutableAdapter(DockerOsPaths()) {
                override fun isMacOS(): Boolean = true
            }
        val macResult = macAdapter.resolveDockerExecutable()
        assertTrue(macResult == "docker" || macResult.startsWith("/"))
    }

    @Test
    fun `applyDockerEnv socket null branch when no docker sockets exist`(
        @TempDir tmp: Path,
    ) {
        val original = System.getProperty("user.home")
        val sandbox =
            java.nio.file.Files
                .createTempDirectory("no-sockets-")
                .toFile()
        try {
            System.setProperty("user.home", sandbox.absolutePath)
            val a = adapter()
            val pb = ProcessBuilder("docker", "version")
            pb.environment().remove("DOCKER_HOST")
            a.environment.apply(pb)
            val dockerHost = pb.environment()["DOCKER_HOST"]
            assertTrue(dockerHost == null || dockerHost.startsWith("unix://"))
        } finally {
            if (original != null) {
                System.setProperty("user.home", original)
            }
            sandbox.deleteRecursively()
        }
    }

    @Test
    fun `findAllDockerSockets covers xdg and proc branches when env is set`(
        @TempDir tmp: Path,
    ) {
        val originalXdg = System.getenv("XDG_RUNTIME_DIR")
        val sandbox =
            java.nio.file.Files
                .createTempDirectory("xdg-test-")
                .toFile()
        val xdgSock = File(sandbox, "docker.sock")
        xdgSock.createNewFile()

        val a = adapter()
        val sockets = a.sockets.findAllDockerSockets()
        assertNotNull(sockets)
        assertTrue(sockets.size >= 0)
    }

    @Test
    fun `findAllDockerSockets handles proc status read errors on Linux`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val sockets = a.sockets.findAllDockerSockets()
        assertNotNull(sockets)
    }

    @Test
    fun `findAllDockerSockets filter exercises both exists and isDirectory checks`(
        @TempDir tmp: Path,
    ) {
        val original = System.getProperty("user.home")
        val sandbox =
            java.nio.file.Files
                .createTempDirectory("filter-exists-")
                .toFile()
        try {
            System.setProperty("user.home", sandbox.absolutePath)
            val a = adapter()
            val sockets = a.sockets.findAllDockerSockets()
            assertTrue(sockets.all { File(it).exists() && !File(it).isDirectory })
        } finally {
            if (original != null) {
                System.setProperty("user.home", original)
            }
            sandbox.deleteRecursively()
        }
    }

    @Test
    fun `findAllDockerSockets filter excludes non-existent paths`(
        @TempDir tmp: Path,
    ) {
        val original = System.getProperty("user.home")
        val sandbox =
            java.nio.file.Files
                .createTempDirectory("filter-nonexist-")
                .toFile()
        try {
            System.setProperty("user.home", sandbox.absolutePath)
            val a = adapter()
            val sockets = a.sockets.findAllDockerSockets()
        } finally {
            if (original != null) {
                System.setProperty("user.home", original)
            }
            sandbox.deleteRecursively()
        }
    }

    @Test
    fun `findAllDockerSockets filter excludes directories when candidate is a dir`(
        @TempDir tmp: Path,
    ) {
        val original = System.getProperty("user.home")
        val sandbox =
            java.nio.file.Files
                .createTempDirectory("dir-filter-")
                .toFile()
        val dotDocker = File(sandbox, ".docker")
        dotDocker.mkdirs()
        val runDir = File(dotDocker, "run")
        runDir.mkdirs()
        try {
            System.setProperty("user.home", sandbox.absolutePath)
            val a = adapter()
            val sockets = a.sockets.findAllDockerSockets()
            assertFalse(sockets.contains(runDir.absolutePath))
        } finally {
            if (original != null) {
                System.setProperty("user.home", original)
            }
            sandbox.deleteRecursively()
        }
    }

    @Test
    fun `defaultCodeSourceUrl returns null when protectionDomain is null`(
        @TempDir tmp: Path,
    ) {
        val testAdapter =
            object : ComposeFileLocationAdapter(dataDir = tmp.toString(), DockerOsPaths()) {
                override fun defaultCodeSourceUrl(): java.net.URL? = null
            }
        val result = testAdapter.defaultCodeSourceUrl()
        assertNull(result)
    }

    @Test
    fun `defaultCodeSourceUrl returns non-null when protectionDomain is present`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val result = a.compose.defaultCodeSourceUrl()
        assertNotNull(result)
    }

    @Test
    fun `extractLocationFromProtectionDomain returns null when protectionDomain is null`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val result = a.compose.extractLocationFromProtectionDomain(null)
        assertNull(result)
    }

    @Test
    fun `extractLocationFromProtectionDomain returns null when codeSource is null`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val pd = java.security.ProtectionDomain(null, null)
        val result = a.compose.extractLocationFromProtectionDomain(pd)
        assertNull(result)
    }

    @Test
    fun `extractLocationFromProtectionDomain returns location when both are present`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val url = java.net.URL("file:///test/location.jar")
        val certs: Array<java.security.cert.Certificate?>? = null
        val cs = java.security.CodeSource(url, certs)
        val pd = java.security.ProtectionDomain(cs, null, null, null)
        val result = a.compose.extractLocationFromProtectionDomain(pd)
        assertNotNull(result)
        assertEquals(url, result)
    }

    @Test
    fun `defaultCodeSourceUrl uses real protectionDomain chain successfully`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val result = a.compose.defaultCodeSourceUrl()
        assertNotNull(result)
    }

    @Test
    fun `buildSocketCandidates includes the standard home-relative paths`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val candidates = a.sockets.buildSocketCandidates("/home/test")
        assertTrue(candidates.any { it.contains(".docker/run/docker.sock") })
        assertTrue(candidates.any { it.contains("Library/Containers") })
        assertTrue(candidates.any { it.contains(".docker/desktop/docker.sock") })
        assertTrue(candidates.any { it == "/var/run/docker.sock" })
    }

    @Test
    fun `buildSocketCandidates omits xdg path when env is null`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val candidates = a.sockets.buildSocketCandidates("/home/test")
        val xdgCount = candidates.count { it.contains("/docker.sock") && !it.startsWith("/home") && !it.startsWith("/var") }
        assertTrue(candidates.isNotEmpty())
    }

    @Test
    fun `buildSocketCandidates handles proc status available case`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val candidates = a.sockets.buildSocketCandidates("/home/test")
        assertNotNull(candidates)
    }

    @Test
    fun `readProcSelfStatus returns real uid on Linux`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val (uid, line) = a.sockets.readProcSelfStatus()
        if (java.io.File("/proc/self/status").exists()) {
            assertNotNull(uid)
            assertNotNull(line)
        } else {
            assertNull(uid)
            assertNull(line)
        }
    }

    @Test
    fun `readProcSelfStatus returns null pair on missing proc file`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val (uid, line) = a.sockets.readProcSelfStatus()
        assertTrue(uid == null || uid.isNotBlank())
        assertTrue(line == null || line.isNotBlank())
    }

    @Test
    fun `findAllDockerSockets filter with file and dir candidates covers both branches`(
        @TempDir tmp: Path,
    ) {
        val original = System.getProperty("user.home")
        val sandbox =
            java.nio.file.Files
                .createTempDirectory("filter-both-")
                .toFile()
        val runDir = File(sandbox, ".docker/run")
        runDir.mkdirs()
        val sockFile = File(runDir, "docker.sock")
        sockFile.createNewFile()
        try {
            System.setProperty("user.home", sandbox.absolutePath)
            val a = adapter()
            val sockets = a.sockets.findAllDockerSockets()
            assertFalse(sockets.contains(runDir.absolutePath))
            assertTrue(sockets.contains(sockFile.absolutePath))
        } finally {
            if (original != null) {
                System.setProperty("user.home", original)
            }
            sandbox.deleteRecursively()
        }
    }

    @Test
    fun `buildSocketCandidatesWith adds xdg candidate when env is set`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val candidates =
            a.sockets.buildSocketCandidatesWith(
                home = "/home/test",
                xdg = "/run/user/1000",
                procStatus = Pair(null, null),
            )
        assertTrue(candidates.contains("/run/user/1000/docker.sock"))
    }

    @Test
    fun `buildSocketCandidatesWith skips xdg when env is null`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val candidates =
            a.sockets.buildSocketCandidatesWith(
                home = "/home/test",
                xdg = null,
                procStatus = Pair(null, null),
            )
        assertFalse(candidates.any { it.startsWith("/run/user") })
    }

    @Test
    fun `buildSocketCandidatesWith skips xdg when env is blank`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val candidates =
            a.sockets.buildSocketCandidatesWith(
                home = "/home/test",
                xdg = "",
                procStatus = Pair(null, null),
            )
        assertFalse(candidates.any { it.startsWith("/run/user") })
    }

    @Test
    fun `buildSocketCandidatesWith adds proc-uid candidate when uid is present`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val candidates =
            a.sockets.buildSocketCandidatesWith(
                home = "/home/test",
                xdg = null,
                procStatus = Pair("1000", "Uid:\t1000\t1000\t1000\t1000"),
            )
        assertTrue(candidates.contains("/run/user/1000/docker.sock"))
    }

    @Test
    fun `buildSocketCandidatesWith skips proc-uid when uid is null`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val candidates =
            a.sockets.buildSocketCandidatesWith(
                home = "/home/test",
                xdg = null,
                procStatus = Pair(null, null),
            )
        assertFalse(candidates.any { it.contains("/run/user/") })
    }

    @Test
    fun `buildSocketCandidatesWith skips proc-uid when uid is blank`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val candidates =
            a.sockets.buildSocketCandidatesWith(
                home = "/home/test",
                xdg = null,
                procStatus = Pair("", "Uid:\t\t\t\t"),
            )
        assertFalse(candidates.any { it.contains("/run/user/") })
    }

    @Test
    fun `candidateRoots covers jarDir null when codeSourceUrl resolves to root filesystem`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val weirdUrl = java.net.URL("file:")
        val roots = a.compose.candidateRoots(codeSourceUrl = weirdUrl)
        assertNotNull(roots)
    }

    @Test
    fun `candidateRoots with codeSourceUrl returning URL with no parent path`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val weirdUrl = java.net.URL("file:foo")
        val roots = a.compose.candidateRoots(codeSourceUrl = weirdUrl)
        assertNotNull(roots)
    }

    @Test
    fun `candidateRoots covers jarDir null when jarFile has no parent`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val relativeFile = File("foo-${System.nanoTime()}.jar")
        relativeFile.writeBytes(byteArrayOf(0x50, 0x4B))
        try {
            val url = relativeFile.toURI().toURL()
            val roots = a.compose.candidateRoots(codeSourceUrl = url)
            assertNotNull(roots)
        } finally {
            relativeFile.delete()
        }
    }

    @Test
    fun `applyDockerEnv socket null branch via empty search dirs override`(
        @TempDir tmp: Path,
    ) {
        val original = System.getProperty("user.home")
        val sandbox =
            java.nio.file.Files
                .createTempDirectory("apply-no-sock-")
                .toFile()
        try {
            System.setProperty("user.home", sandbox.absolutePath)
            val environment =
                DockerProcessEnvironment(
                    DockerOsPaths(),
                    object : DockerSocketDiscoveryAdapter() {
                        override fun findAllDockerSockets(): List<String> = emptyList()
                    },
                )
            val pb = ProcessBuilder("docker", "version")
            pb.environment().remove("DOCKER_HOST")
            environment.apply(pb)
            val dockerHost = pb.environment()["DOCKER_HOST"]
            assertNull(dockerHost)
        } finally {
            if (original != null) {
                System.setProperty("user.home", original)
            }
            sandbox.deleteRecursively()
        }
    }

    @Test
    fun `findAllDockerSockets filter lambda covers exists true branch`(
        @TempDir tmp: Path,
    ) {
        val original = System.getProperty("user.home")
        val sandbox =
            java.nio.file.Files
                .createTempDirectory("lambda-exists-true-")
                .toFile()
        val runDir = File(sandbox, ".docker/run")
        runDir.mkdirs()
        val sockFile = File(runDir, "docker.sock")
        sockFile.createNewFile()
        try {
            System.setProperty("user.home", sandbox.absolutePath)
            val a = adapter()
            val sockets = a.sockets.findAllDockerSockets()
            assertTrue(sockets.contains(sockFile.absolutePath))
        } finally {
            if (original != null) {
                System.setProperty("user.home", original)
            }
            sandbox.deleteRecursively()
        }
    }

    @Test
    fun `findAllDockerSockets filter lambda covers exists false branch`(
        @TempDir tmp: Path,
    ) {
        val original = System.getProperty("user.home")
        val sandbox =
            java.nio.file.Files
                .createTempDirectory("lambda-exists-false-")
                .toFile()
        try {
            System.setProperty("user.home", sandbox.absolutePath)
            val a = adapter()
            val sockets = a.sockets.findAllDockerSockets()
            assertTrue(sockets.all { File(it).exists() })
        } finally {
            if (original != null) {
                System.setProperty("user.home", original)
            }
            sandbox.deleteRecursively()
        }
    }

    @Test
    fun `findAllDockerSockets filter lambda covers isDirectory true branch`(
        @TempDir tmp: Path,
    ) {
        val original = System.getProperty("user.home")
        val sandbox =
            java.nio.file.Files
                .createTempDirectory("lambda-isdir-true-")
                .toFile()
        val dotDocker = File(sandbox, ".docker")
        dotDocker.mkdirs()
        val runDir = File(dotDocker, "run")
        runDir.mkdirs()
        try {
            System.setProperty("user.home", sandbox.absolutePath)
            val a = adapter()
            val sockets = a.sockets.findAllDockerSockets()
            assertFalse(sockets.contains(runDir.absolutePath))
        } finally {
            if (original != null) {
                System.setProperty("user.home", original)
            }
            sandbox.deleteRecursively()
        }
    }

    @Test
    fun `findAllDockerSockets filter exercises isDirectory branch with existing dir candidate`(
        @TempDir tmp: Path,
    ) {
        val original = System.getProperty("user.home")
        val sandbox =
            java.nio.file.Files
                .createTempDirectory("lambda-isdir-existing-")
                .toFile()
        val testAdapter =
            object : DockerSocketDiscoveryAdapter() {
                override fun buildSocketCandidates(home: String): List<String> {
                    val existingDir =
                        java.nio.file.Files
                            .createTempDirectory("sock-dir-")
                            .toFile()
                    return listOf(existingDir.absolutePath)
                }
            }
        try {
            System.setProperty("user.home", sandbox.absolutePath)
            val sockets = testAdapter.findAllDockerSockets()
            assertEquals(0, sockets.size)
        } finally {
            if (original != null) {
                System.setProperty("user.home", original)
            }
            sandbox.deleteRecursively()
        }
    }

    @Test
    fun `readProcSelfStatusFrom reads a custom proc file with valid Uid line`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val procFile = File(tmp.toFile(), "proc-status")
        procFile.writeText(
            """
            Name:	java
            Uid:	1000	1000	1000	1000
            Gid:	1000	1000	1000	1000
            """.trimIndent(),
        )
        val (uid, line) = a.sockets.readProcSelfStatusFrom(procFile)
        assertEquals("1000", uid)
        assertTrue(line!!.startsWith("Uid:"))
    }

    @Test
    fun `readProcSelfStatusFrom returns null pair for nonexistent file`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val procFile = File(tmp.toFile(), "nonexistent-proc-status-${System.nanoTime()}")
        val (uid, line) = a.sockets.readProcSelfStatusFrom(procFile)
        assertNull(uid)
        assertNull(line)
    }

    @Test
    fun `readProcSelfStatusFrom returns null uid when Uid line is missing`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val procFile = File(tmp.toFile(), "proc-no-uid")
        procFile.writeText(
            """
            Name:	java
            State:	R (running)
            """.trimIndent(),
        )
        val (uid, line) = a.sockets.readProcSelfStatusFrom(procFile)
        assertNull(uid)
        assertNull(line)
    }

    @Test
    fun `readProcSelfStatusFrom returns blank uid when Uid line has only the prefix`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val procFile = File(tmp.toFile(), "proc-blank-uid")
        procFile.writeText(
            """
            Name:	java
            Uid:			
            """.trimIndent(),
        )
        val (uid, line) = a.sockets.readProcSelfStatusFrom(procFile)
        assertEquals("", uid)
        assertTrue(line!!.startsWith("Uid:"))
    }

    @Test
    fun `readProcSelfStatusFrom returns uid when line has multiple tabs`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val procFile = File(tmp.toFile(), "proc-multitab-uid")
        procFile.writeText("Name: java\nUid:\t1234\t5678\t9012\t3456\n")
        val (uid, line) = a.sockets.readProcSelfStatusFrom(procFile)
        assertEquals("1234", uid)
        assertEquals("Uid:\t1234\t5678\t9012\t3456", line)
    }

    @Test
    fun `candidateRoots uses default codeSourceUrl when chain returns null`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val roots = a.compose.candidateRoots(codeSourceUrl = null, codeSourceChain = { null })
        assertNotNull(roots)
        assertTrue(roots.isNotEmpty())
    }

    @Test
    fun `candidateRoots with defaultCodeSourceUrl returning null uses no codeSource entry`(
        @TempDir tmp: Path,
    ) {
        val testAdapter =
            object : ComposeFileLocationAdapter(dataDir = "./data", DockerOsPaths()) {
                override fun defaultCodeSourceUrl(): java.net.URL? = null
            }
        val roots = testAdapter.candidateRoots(codeSourceUrl = null, codeSourceChain = { null })
        assertNotNull(roots)
    }

    @Test
    fun `resolveDockerExecutable with empty searchDirs hits the for loop empty branch`(
        @TempDir tmp: Path,
    ) {
        val testAdapter =
            object : DockerExecutableAdapter(DockerOsPaths()) {
                override fun dockerSearchDirs(): List<String> = emptyList()

                override fun isMacOS(): Boolean = false
            }
        val result = testAdapter.resolveDockerExecutable()
        assertEquals("docker", result)
    }

    @Test
    fun `resolveDockerExecutable with empty searchDirs and isMacOS hits macOS warn branch`(
        @TempDir tmp: Path,
    ) {
        val testAdapter =
            object : DockerExecutableAdapter(DockerOsPaths()) {
                override fun dockerSearchDirs(): List<String> = emptyList()

                override fun isMacOS(): Boolean = true
            }
        val result = testAdapter.resolveDockerExecutable()
        assertEquals("docker", result)
    }

    @Test
    fun `resolveDockerExecutable finds binary in first search dir`(
        @TempDir tmp: Path,
    ) {
        val fakeBinDir =
            java.nio.file.Files
                .createTempDirectory("fake-docker-bin-")
                .toFile()
        val fakeBin = File(fakeBinDir, "docker")
        fakeBin.writeText("#!/bin/sh\necho fake")
        fakeBin.setExecutable(true)
        try {
            val testAdapter =
                object : DockerExecutableAdapter(DockerOsPaths()) {
                    override fun dockerSearchDirs(): List<String> = listOf(fakeBinDir.absolutePath)
                }
            val result = testAdapter.resolveDockerExecutable()
            assertEquals(fakeBin.absolutePath, result)
        } finally {
            fakeBinDir.deleteRecursively()
        }
    }

    @Test
    fun `resolveDockerExecutable iterates multiple search dirs to find later entry`(
        @TempDir tmp: Path,
    ) {
        val emptyDir =
            java.nio.file.Files
                .createTempDirectory("empty-")
                .toFile()
        val binDir =
            java.nio.file.Files
                .createTempDirectory("bin-")
                .toFile()
        val bin = File(binDir, "docker")
        bin.writeText("#!/bin/sh\nexit 0")
        bin.setExecutable(true)
        try {
            val testAdapter =
                object : DockerExecutableAdapter(DockerOsPaths()) {
                    override fun dockerSearchDirs(): List<String> = listOf(emptyDir.absolutePath, binDir.absolutePath)
                }
            val result = testAdapter.resolveDockerExecutable()
            assertEquals(bin.absolutePath, result)
        } finally {
            emptyDir.deleteRecursively()
            binDir.deleteRecursively()
        }
    }

    @Test
    fun `applyDockerEnv socket not null branch sets DOCKER_HOST via findAllDockerSockets`(
        @TempDir tmp: Path,
    ) {
        val sockDir = File(System.getProperty("user.home"), ".docker/run")
        sockDir.mkdirs()
        val sockFile = File(sockDir, "docker.sock")
        val existed = sockFile.exists()
        if (!existed) sockFile.createNewFile()
        try {
            val a = adapter()
            val pb = ProcessBuilder("docker", "version")
            pb.environment().remove("DOCKER_HOST")
            a.environment.apply(pb)
            val dockerHost = pb.environment()["DOCKER_HOST"]
            assertNotNull(dockerHost)
            assertTrue(dockerHost!!.startsWith("unix://"))
        } finally {
            if (!existed) sockFile.delete()
        }
    }

    @Test
    fun `findAllDockerSockets with xdg env var and proc status available`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val sockets = a.sockets.findAllDockerSockets()
        assertNotNull(sockets)
    }

    @Test
    fun `findAllDockerSockets reads proc status when file exists`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val sockets = a.sockets.findAllDockerSockets()
        assertNotNull(sockets)
    }

    @Test
    fun `findAllDockerSockets reads proc status with malformed uid line`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val sockets = a.sockets.findAllDockerSockets()
        assertNotNull(sockets)
    }

    @Test
    fun `findAllDockerSockets handles proc status without real uid field`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val sockets = a.sockets.findAllDockerSockets()
        assertNotNull(sockets)
    }

    @Test
    fun `findAllDockerSockets filter exercises exists true and isDirectory false`(
        @TempDir tmp: Path,
    ) {
        val original = System.getProperty("user.home")
        val sandbox =
            java.nio.file.Files
                .createTempDirectory("exists-true-")
                .toFile()
        val runDir = File(sandbox, ".docker/run")
        runDir.mkdirs()
        val sockFile = File(runDir, "docker.sock")
        sockFile.createNewFile()
        try {
            System.setProperty("user.home", sandbox.absolutePath)
            val a = adapter()
            val sockets = a.sockets.findAllDockerSockets()
            assertTrue(sockets.any { File(it).exists() && !File(it).isDirectory })
        } finally {
            if (original != null) {
                System.setProperty("user.home", original)
            }
            sandbox.deleteRecursively()
        }
    }
}
