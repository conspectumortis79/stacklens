package com.stacklens.adapter.out.shared

import com.stacklens.shared.ProcessUtils
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

object MakefileRunner {
    private const val MAX_CAPTURED_OUTPUT_CHARS = 4 * 1024 * 1024
    private const val DRAIN_JOIN_MILLIS = 2_000L

    fun makefileHasTarget(
        repoPath: String,
        target: String,
    ): Boolean {
        val repoDir = File(repoPath)
        val mainMakefile =
            listOf("Makefile", "makefile", "GNUmakefile")
                .map { File(repoDir, it) }
                .firstOrNull { it.exists() } ?: return false
        return try {
            collectMakefileLines(repoDir, mainMakefile, mutableSetOf())
                .any { line -> line.matches(Regex("^${Regex.escape(target)}\\s*:.*")) }
        } catch (_: Exception) {
            false
        }
    }

    private fun collectMakefileLines(
        repoDir: File,
        file: File,
        visited: MutableSet<String>,
    ): List<String> {
        if (!visited.add(file.canonicalPath)) return emptyList()

        val result = mutableListOf<String>()

        for (line in file.readLines()) {
            result.add(line)
            val includeMatch = Regex("""^-?include\s+(.+)""").find(line.trim()) ?: continue
            includeMatch.groupValues[1]
                .trim()
                .split(Regex("""\s+"""))
                .forEach { pattern ->
                    val includedFile = repoDir.resolve(pattern)
                    if (includedFile.exists() && includedFile.isFile) {
                        result.addAll(collectMakefileLines(repoDir, includedFile, visited))
                    }
                }
        }

        return result
    }

    fun findMakeTarget(
        repoPath: String,
        vararg candidates: String,
    ): String? = candidates.firstOrNull { makefileHasTarget(repoPath, it) }

    fun runMakeTarget(
        repoPath: String,
        target: String,
        timeoutSecs: Long = 120,
        startProcess: (List<String>, File) -> Process = ::startMakeProcess,
    ): Pair<Int, String> {
        val makeBin = ProcessUtils.resolveMakeBinary()
        val proc =
            try {
                startProcess(listOf(makeBin, target), File(repoPath))
            } catch (e: Exception) {
                return Pair(-1, "Error starting make $target: ${e.message}")
            }
        val outBuf = StringBuilder()
        val drainThread =
            Thread { drainOutput(proc.inputStream, outBuf) }.also {
                it.isDaemon = true
                it.start()
            }
        return try {
            val done = proc.waitFor(timeoutSecs, TimeUnit.SECONDS)
            if (!done) {
                ProcessUtils.killProcessTree(proc)
                drainThread.join(DRAIN_JOIN_MILLIS)
                Pair(-1, "Timeout nach ${timeoutSecs}s")
            } else {
                drainThread.join(DRAIN_JOIN_MILLIS)
                Pair(proc.exitValue(), outBuf.toString())
            }
        } catch (e: InterruptedException) {
            ProcessUtils.killProcessTree(proc)
            Thread.currentThread().interrupt()
            throw e
        } catch (e: Exception) {
            ProcessUtils.killProcessTree(proc)
            Pair(-1, "Error running make $target: ${e.message}")
        }
    }

    internal fun drainOutput(
        input: InputStream,
        into: StringBuilder,
    ) {
        val reader = InputStreamReader(input, Charsets.UTF_8)
        try {
            val buf = CharArray(8 * 1024)
            while (true) {
                val read = reader.read(buf)
                if (read < 0) break
                into.append(buf, 0, read)
                if (into.length > MAX_CAPTURED_OUTPUT_CHARS) break
            }
        } catch (_: Exception) {
        } finally {
            try {
                reader.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun startMakeProcess(
        command: List<String>,
        workDir: File,
    ): Process = ProcessBuilder(command).directory(workDir).redirectErrorStream(true).start()
}
