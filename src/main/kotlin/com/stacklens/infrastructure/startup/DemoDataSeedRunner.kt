package com.stacklens.infrastructure.startup

import com.stacklens.application.port.out.platform.DemoDataPort
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty("stacklens.seed-demo-data", havingValue = "true", matchIfMissing = false)
class DemoDataSeedRunner(
    private val demoData: ObjectProvider<DemoDataPort>,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        demoData.ifAvailable?.reseed()
    }
}
