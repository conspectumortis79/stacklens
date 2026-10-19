package com.stacklens.shared

import java.io.File
import java.util.concurrent.TimeUnit

object ProcessUtils {
    internal val GIT_CANDIDATES: List<String> =
        listOf(
            "/opt/homebrew/bin/git",
            "/usr/local/bin/git",
            "/usr/bin/git",
            "/bin/git",
        )

    internal val MAKE_CANDIDATES: List<String> =
        listOf(
            "/opt/homebrew/bin/make",
            "/usr/local/bin/make",
            "/usr/bin/make",
            "/bin/make",
        )

    fun resolveGitBinary(): String = firstExecutable(GIT_CANDIDATES, "git")

    fun resolveMakeBinary(): String = firstExecutable(MAKE_CANDIDATES, "make")

    internal fun firstExecutable(
        candidates: List<String>,
        fallback: String,
    ): String = candidates.firstOrNull { File(it).canExecute() } ?: fallback

    fun killProcessTree(
        proc: Process,
        graceMillis: Long = DEFAULT_GRACE_MILLIS,
    ) {
        try {
            proc.descendants().forEach { it.destroy() }
            proc.destroy()
            if (!proc.waitFor(graceMillis, TimeUnit.MILLISECONDS)) {
                proc.descendants().forEach { it.destroyForcibly() }
                proc.destroyForcibly()
                proc.waitFor(graceMillis, TimeUnit.MILLISECONDS)
            }
        } catch (_: Exception) {
        }
    }

    private const val DEFAULT_GRACE_MILLIS = 2_000L
}
