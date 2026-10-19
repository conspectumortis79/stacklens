package com.stacklens.application.port.out.process

interface GitBinaryPort {
    fun resolveGitBinary(): String
}

interface ProcessRunPort {
    fun killProcessTree(process: ManagedProcess)

    fun run(
        cmd: List<String>,
        timeoutSec: Long = 60L,
        workDir: java.io.File? = null,
    ): ProcessResult

    fun launch(cmd: List<String>): ManagedProcess

    fun runStreaming(
        cmd: List<String>,
        workDir: java.io.File? = null,
        timeoutSec: Long = 60L,
        onLine: (String) -> Unit,
    ): Int
}

interface MakefileOpsPort {
    fun resolveMakeBinary(): String

    fun makefileHasTarget(
        repoPath: String,
        target: String,
    ): Boolean

    fun findMakeTarget(
        repoPath: String,
        vararg candidates: String,
    ): String?

    fun runMakeTarget(
        repoPath: String,
        target: String,
        timeoutSecs: Long = 120L,
    ): Pair<Int, String>
}

data class ProcessResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) {
    val success: Boolean get() = exitCode == 0
}

interface GitCommandPort :
    GitBinaryPort,
    ProcessRunPort

interface MakefileCommandPort :
    MakefileOpsPort,
    ProcessRunPort

interface AgentProcessPort :
    GitCommandPort,
    MakefileCommandPort

interface ProcessPort : AgentProcessPort
