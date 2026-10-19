package com.stacklens.application.port.out.platform

interface HostDirectoryPort {
    fun homeDirectory(): String

    fun workingDirectory(): String

    fun temporaryDirectory(): String
}
