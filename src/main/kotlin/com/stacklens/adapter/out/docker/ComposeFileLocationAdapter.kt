package com.stacklens.adapter.out.docker

import com.stacklens.application.port.out.docker.ComposeFileLocationPort
import com.stacklens.domain.util.quietly
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.File

@Component
open class ComposeFileLocationAdapter(
    @Value("\${stacklens.data-dir:./data}") private val dataDir: String,
    private val osPaths: DockerOsPaths,
) : ComposeFileLocationPort {
    private val log = LoggerFactory.getLogger(ComposeFileLocationAdapter::class.java)

    override fun resolveComposeFile(): String? =
        runCatching {
            resolveComposeFileInternal()?.absolutePath
        }.getOrElse { e ->
            log.error("resolveComposeFile failed: {}", e.message, e)
            null
        }

    override fun containingDirectory(path: String): String? = File(path).parentFile?.absolutePath

    internal open fun resolveComposeFileInternal(): File? {
        val candidateRoots = candidateRoots()
        val found = searchForComposeFile(candidateRoots)
        if (found == null) {
            log.warn("No docker-compose.yml found in any candidate location")
            return null
        }

        if (isMacOS() && !isDockerAccessibleOnMacOS(found)) {
            val synced = syncDockerDirToUserSpace(found)
            if (synced != null) return synced
        }
        return found
    }

    override fun findComposeContainerNames(composeFile: String): List<String> =
        readComposeLines(File(composeFile))
            ?.mapNotNull { line ->
                CONTAINER_NAME_REGEX
                    .find(line)
                    ?.groupValues
                    ?.get(1)
                    ?.takeIf { it.isNotBlank() }
            } ?: emptyList()

    override fun resolveComposeSiblingFile(
        composeFile: String,
        relativePath: String,
    ): String? = File(File(composeFile).parentFile, relativePath).takeIf { it.isFile }?.absolutePath

    internal fun readComposeLines(composeFile: File): List<String>? =
        runCatching { composeFile.readLines() }
            .onFailure { e -> log.warn("[DockerCompose] could not read {}: {}", composeFile.absolutePath, e.message) }
            .getOrNull()

    internal open fun candidateRoots(
        codeSourceUrl: java.net.URL? = null,
        codeSourceChain: () -> java.net.URL? = { defaultCodeSourceUrl() },
    ): List<File> =
        buildList {
            quietly { add(File(System.getProperty("user.dir", ".")).canonicalFile) }
            runCatching {
                val loc = codeSourceUrl ?: codeSourceChain()
                if (loc != null) {
                    val jarFile = File(loc.toURI()).canonicalFile
                    val jarDir = if (jarFile.isFile) jarFile.parentFile else jarFile
                    add(jarDir)
                }
            }
            quietly {
                var cur: File? = File(dataDir).canonicalFile
                while (cur != null) {
                    add(cur)
                    cur = cur.parentFile
                }
            }
        }.distinct()

    internal fun searchForComposeFile(candidateRoots: List<File>): File? {
        var found: File? = null
        for (root in candidateRoots) {
            for (name in listOf("docker-compose.yml", "docker-compose.yaml")) {
                val f = File(root, "docker/$name")
                if (f.isFile) {
                    log.debug("Resolved StackLens compose file: {}", f)
                    found = f
                    break
                }
            }
            if (found != null) break
        }
        if (found == null) {
            for (root in candidateRoots) {
                for (name in listOf("docker-compose.yml", "docker-compose.yaml")) {
                    val f = File(root, name)
                    if (f.isFile) {
                        log.info("Resolved compose file (root fallback): {}", f)
                        found = f
                        break
                    }
                }
                if (found != null) break
            }
        }
        return found
    }

    internal open fun defaultCodeSourceUrl(): java.net.URL? {
        val pd = ComposeFileLocationAdapter::class.java.protectionDomain
        return extractLocationFromProtectionDomain(pd)
    }

    internal fun extractLocationFromProtectionDomain(pd: java.security.ProtectionDomain?): java.net.URL? = pd?.codeSource?.location

    internal open fun isMacOS(): Boolean = osPaths.isMacOS()

    internal open fun isDockerAccessibleOnMacOS(file: File): Boolean {
        val p = file.absolutePath
        return p.startsWith("/Users/") ||
            p.startsWith("/tmp/") ||
            p.startsWith("/Volumes/") ||
            p.startsWith("/private/")
    }

    internal fun syncDockerDirToUserSpace(composeFile: File): File? {
        val dockerDir = composeFile.parentFile ?: return null
        val userHome = System.getProperty("user.home") ?: return null
        val targetDir = File(userHome, ".stacklens/docker")
        return runCatching {
            targetDir.mkdirs()
            dockerDir.copyRecursively(targetDir, overwrite = true)
            log.info("Synced docker/ to user-accessible path: {}", targetDir)
            File(targetDir, composeFile.name).takeIf { it.isFile }
        }.getOrElse { e ->
            log.error("Failed to sync docker/ to ~/.stacklens/docker/: {}", e.message, e)
            null
        }
    }

    private companion object {
        private val CONTAINER_NAME_REGEX: Regex = Regex("""^\s*container_name:\s*(.+?)\s*$""")
    }
}
