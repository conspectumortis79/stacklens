package com.stacklens.application.port.out.repo

interface GitClonePort {
    fun clone(
        authenticatedUrl: String,
        parentDir: java.io.File,
        safeName: String,
        timeoutMinutes: Long = 10L,
    ): String
}
