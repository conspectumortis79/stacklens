package com.stacklens.application.port.out.platform

data class HostSystemInfo(
    val osName: String,
    val osArch: String,
    val osVersion: String,
    val javaVersion: String,
)

interface SystemInfoPort {
    fun hostSystem(): HostSystemInfo
}
