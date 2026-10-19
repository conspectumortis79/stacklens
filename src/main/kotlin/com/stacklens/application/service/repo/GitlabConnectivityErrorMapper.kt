package com.stacklens.application.service.repo

import com.stacklens.application.port.inbound.repo.GitlabConnectivityErrorUseCase
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.stereotype.ApplicationService
import java.net.UnknownHostException

@ApplicationService
class GitlabConnectivityErrorMapper(
    private val i18n: I18nPort,
) : GitlabConnectivityErrorUseCase {
    override fun friendlyErrorMessage(e: Throwable): String? {
        val chain = generateSequence(e) { it.cause }
        val unknownHost = chain.filterIsInstance<UnknownHostException>().firstOrNull()
        if (unknownHost != null) {
            val host =
                unknownHost
                    .message
                    ?.substringBefore(" ")
                    ?.trim()
                    ?.ifBlank { null }
            return if (host != null) {
                i18n.tFor("de", "config.be_gitlab_host_unresolved_with_host", host)
            } else {
                i18n.tFor("de", "config.be_gitlab_host_unresolved")
            }
        }
        val msgChain = chain.mapNotNull { it.message }.joinToString(" | ")
        val isUnreachable =
            msgChain.contains("Connection refused", ignoreCase = true) ||
                msgChain.contains("connect timed out", ignoreCase = true) ||
                msgChain.contains("Failed to connect", ignoreCase = true)
        if (isUnreachable) {
            return i18n.tFor("de", "config.be_gitlab_unreachable")
        }
        return null
    }
}
