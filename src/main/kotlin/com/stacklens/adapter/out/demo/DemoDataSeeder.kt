package com.stacklens.adapter.out.demo

import com.stacklens.application.port.out.platform.DemoDataPort
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty("stacklens.seed-demo-data", havingValue = "true", matchIfMissing = false)
class DemoDataSeeder(
    private val seeders: List<DemoDataSeederStrategy>,
) : DemoDataPort {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun reseed() {
        log.info("[DemoDataSeeder] Seeding demo connections …")
        seeders.forEach { it.seed() }
        log.info("[DemoDataSeeder] Done.")
    }
}
