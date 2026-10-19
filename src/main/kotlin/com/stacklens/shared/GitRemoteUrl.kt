package com.stacklens.shared

import java.io.File
import java.util.concurrent.TimeUnit

internal fun gitRemoteUrl(
    workDir: File,
    remoteName: String = "origin",
    gitBinary: String = "git",
    timeoutSeconds: Long = GIT_REMOTE_TIMEOUT_SECONDS,
): String? =
    runCatching {
        val capture = File.createTempFile("stacklens-git-remote-", ".txt")
        val process =
            ProcessBuilder(gitBinary, "remote", "get-url", remoteName)
                .directory(workDir)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.to(capture))
                .start()
        try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                return@runCatching null
            }
            capture
                .readText(Charsets.UTF_8)
                .trim()
                .takeIf { process.exitValue() == 0 && it.isNotBlank() }
        } finally {
            if (process.isAlive) {
                ProcessUtils.killProcessTree(process)
            }
            capture.delete()
        }
    }.getOrNull()

internal fun gitRemotePathWithNamespace(remoteUrl: String): String? {
    val pathWithNamespace =
        remoteUrl.removeSuffix(".git").let { url ->
            HTTPS_REMOTE.find(url)?.groupValues?.get(1)
                ?: url.lastIndexOf(':').takeIf { it >= 0 }?.let { colonIdx -> url.substring(colonIdx + 1) }
        }
    return pathWithNamespace?.takeIf { it.isNotBlank() }
}

private const val GIT_REMOTE_TIMEOUT_SECONDS = 30L

private val HTTPS_REMOTE = Regex("""https?://[^/]+/(.+)""")
