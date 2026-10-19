package com.stacklens.application.port.inbound.repo

interface GitlabConnectivityErrorUseCase {
    fun friendlyErrorMessage(e: Throwable): String?
}
