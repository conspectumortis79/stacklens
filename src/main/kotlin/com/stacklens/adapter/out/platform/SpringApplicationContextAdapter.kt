package com.stacklens.adapter.out.platform

import com.stacklens.application.port.out.platform.ApplicationContextPort
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.stereotype.Component

@Component
class SpringApplicationContextAdapter(
    private val applicationContext: ConfigurableApplicationContext,
) : ApplicationContextPort {
    override fun shutdown() {
        runCatching { applicationContext.close() }
    }
}
