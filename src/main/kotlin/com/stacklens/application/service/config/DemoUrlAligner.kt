package com.stacklens.application.service.config

import com.stacklens.application.port.out.platform.DemoRuntimeHostPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.ConnectionEntity

@ApplicationService
class DemoUrlAligner(
    private val runtimeHosts: DemoRuntimeHostPort,
) {
    fun <T : ConnectionEntity> alignUrls(
        entries: List<T>,
        urlOf: (T) -> String?,
        urlSetter: (T, String) -> Unit,
        persist: (T) -> Unit,
    ): List<String> = alignUrls(entries, urlOf, urlSetter, persist, runtimeHosts::alignedServiceUrl)

    fun <T : ConnectionEntity> alignUrls(
        entries: List<T>,
        urlOf: (T) -> String?,
        urlSetter: (T, String) -> Unit,
        persist: (T) -> Unit,
        alignWith: (String) -> String,
    ): List<String> = align(entries, { urlOf(it).orEmpty() }, urlSetter, persist, alignWith)

    fun <T : ConnectionEntity> alignHosts(
        entries: List<T>,
        hostOf: (T) -> String,
        hostSetter: (T, String) -> Unit,
        persist: (T) -> Unit,
    ): List<String> = align(entries, hostOf, hostSetter, persist, runtimeHosts::alignedHost)

    fun alignedServiceUrl(url: String): String = runtimeHosts.alignedServiceUrl(url)

    fun alignedMcpServerUrl(url: String): String = runtimeHosts.alignedMcpServerUrl(url)

    private fun <T : ConnectionEntity> align(
        entries: List<T>,
        currentOf: (T) -> String,
        applyAligned: (T, String) -> Unit,
        persist: (T) -> Unit,
        alignWith: (String) -> String,
    ): List<String> {
        val changes = mutableListOf<String>()
        entries
            .filter { it.name.startsWith(DEMO_NAME_PREFIX) }
            .forEach { entry ->
                val current = currentOf(entry)
                if (current.isNotBlank()) {
                    val aligned = alignWith(current)
                    if (aligned != current) {
                        applyAligned(entry, aligned)
                        persist(entry)
                        changes += "${entry.name}→$aligned"
                    }
                }
            }
        return changes
    }

    companion object {
        const val DEMO_NAME_PREFIX = "Demo"
    }
}
