package com.stacklens.adapter.out.codeqa

import com.stacklens.application.port.out.MrFetchPort
import com.stacklens.application.port.out.process.ProcessRunPort
import java.io.File

class GitCloneOrchestrator(
    private val processPort: ProcessRunPort,
) {
    data class CloneResult(
        val success: Boolean,
        val errorMessage: String? = null,
    )

    fun cloneBranch(
        cloneUrl: String,
        tmpDir: File,
        mrData: MrFetchPort.MrData,
    ): CloneResult {
        val cloneResult =
            processPort.run(
                listOf("git", "clone", "--branch", mrData.sourceBranch, "--depth", "1", cloneUrl, tmpDir.absolutePath),
                timeoutSec = 300,
                workDir = tmpDir.parentFile,
            )
        if (cloneResult.success) return CloneResult(true)
        val headSha = mrData.diffHeadSha.takeIf { it.isNotBlank() }
        if (headSha != null && cloneResult.exitCode == 128) {
            return fallbackShaFetch(cloneUrl, tmpDir, headSha)
        }
        val safeOutput =
            "${cloneResult.stdout}\n${cloneResult.stderr}"
                .replace(Regex("https?://[^@]+@"), "https://***@")
                .take(600)
        return CloneResult(false, "git clone failed (exit ${cloneResult.exitCode}):\n$safeOutput")
    }

    private fun fallbackShaFetch(
        cloneUrl: String,
        tmpDir: File,
        headSha: String,
    ): CloneResult {
        val initOk = processPort.run(listOf("git", "init", tmpDir.absolutePath), timeoutSec = 30, workDir = tmpDir.parentFile).success
        if (!initOk) return CloneResult(false, "git init failed")
        val remoteOk =
            processPort
                .run(
                    listOf("git", "-C", tmpDir.absolutePath, "remote", "add", "origin", cloneUrl),
                    timeoutSec = 30,
                    workDir = tmpDir,
                ).success
        if (!remoteOk) return CloneResult(false, "git remote add failed")
        val fetchOk =
            processPort
                .run(
                    listOf("git", "-C", tmpDir.absolutePath, "fetch", "--depth", "1", "origin", headSha),
                    timeoutSec = 300,
                    workDir = tmpDir,
                ).success
        if (!fetchOk) return CloneResult(false, "git fetch failed for $headSha")
        val checkoutOk =
            processPort
                .run(
                    listOf("git", "-C", tmpDir.absolutePath, "checkout", "FETCH_HEAD"),
                    timeoutSec = 30,
                    workDir = tmpDir,
                ).success
        if (!checkoutOk) return CloneResult(false, "git checkout failed")
        return CloneResult(true)
    }
}
