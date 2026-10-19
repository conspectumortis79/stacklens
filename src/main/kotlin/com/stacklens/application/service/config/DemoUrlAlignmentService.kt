package com.stacklens.application.service.config

import com.stacklens.application.port.inbound.config.DemoUrlAlignmentUseCase
import com.stacklens.application.port.out.config.DemoUrlAlignmentPort
import com.stacklens.application.stereotype.ApplicationService
import org.slf4j.LoggerFactory

@ApplicationService
class DemoUrlAlignmentService(
    private val sections: List<DemoUrlAlignmentSection>,
) : DemoUrlAlignmentUseCase,
    DemoUrlAlignmentPort {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun alignAll(): List<String> = sections.flatMap { section -> alignSection(section) }

    private fun alignSection(section: DemoUrlAlignmentSection): List<String> =
        runCatching { section.align() }
            .onFailure { log.warn("[DemoUrlAlignment] {} alignment skipped: {}", section.label, it.message) }
            .getOrDefault(emptyList())
            .map { "${section.label}:$it" }
}
