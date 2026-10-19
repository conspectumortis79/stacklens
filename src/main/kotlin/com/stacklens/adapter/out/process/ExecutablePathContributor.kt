package com.stacklens.adapter.out.process

import com.stacklens.application.port.out.platform.OsType

data class ExecutableHost(
    val osType: OsType,
    val homeDirectory: String,
)

interface ExecutablePathContributor {
    fun supports(executable: String): Boolean

    fun directories(
        executable: String,
        host: ExecutableHost,
    ): List<String>
}
