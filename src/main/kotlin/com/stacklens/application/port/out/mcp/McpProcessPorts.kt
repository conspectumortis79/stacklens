package com.stacklens.application.port.out.mcp

import java.io.File

interface McpEnvironmentPort {
    fun getCurrentDir(): File

    fun getJavaHome(): File?

    fun getUserHome(): String

    fun getOsName(): String

    fun getEnv(name: String): String?

    fun getProperty(name: String): String?

    fun getProcessPid(): Long

    fun getProcessExecutable(): String?
}

interface McpProcessSpawnerPort {
    fun spawn(
        command: List<String>,
        workingDir: File?,
        environment: Map<String, String>,
        redirectErrorStream: Boolean = true,
    ): Process

    fun runAndCapture(
        command: List<String>,
        workingDir: File?,
        timeoutSeconds: Long = 3,
    ): String
}

interface McpPortProbePort {
    fun isInUse(
        host: String,
        port: Int,
        timeoutMs: Int,
    ): Boolean
}

interface McpOrphanProcessKillerPort {
    fun killProcessOnPort(port: Int)
}
