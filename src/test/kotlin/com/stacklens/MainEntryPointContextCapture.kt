package com.stacklens

import org.springframework.boot.context.event.ApplicationContextInitializedEvent
import org.springframework.context.ApplicationListener
import org.springframework.context.ConfigurableApplicationContext
import java.util.concurrent.atomic.AtomicReference

class MainEntryPointContextCapture : ApplicationListener<ApplicationContextInitializedEvent> {
    override fun onApplicationEvent(event: ApplicationContextInitializedEvent) {
        val environment = event.applicationContext.environment
        if (environment.getProperty(MARKER_PROPERTY) == MARKER_VALUE) {
            CAPTURED.set(event.applicationContext)
        }
    }

    companion object {
        const val MARKER_PROPERTY = "stacklens.test.main-entrypoint-capture"
        const val MARKER_VALUE = "on"

        private val CAPTURED = AtomicReference<ConfigurableApplicationContext?>()

        fun takeCaptured(): ConfigurableApplicationContext? = CAPTURED.getAndSet(null)
    }
}
