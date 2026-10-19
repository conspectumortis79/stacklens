package com.stacklens.application.service.loadtest

import com.stacklens.application.port.out.process.ManagedProcess
import java.io.File

data class K6Run(
    val process: ManagedProcess,
    val stdoutBuf: StringBuilder,
    val stderrBuf: StringBuilder,
    val summaryFile: File,
    val scriptHeader: String? = null,
)
