package com.stacklens.infrastructure.config

import com.stacklens.application.service.AiAnalysisService
import com.stacklens.application.service.config.AiConfigService
import com.stacklens.application.stereotype.ManuallyWired
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.core.type.AnnotationMetadata

class ApplicationServiceRegistrarTest {
    @Test
    fun `manually wired services are skipped while scanned services are registered`() {
        val registry = DefaultListableBeanFactory()

        ApplicationServiceRegistrar()
            .registerBeanDefinitions(AnnotationMetadata.introspect(ApplicationCoreConfig::class.java), registry)

        assertFalse(registry.containsBeanDefinition("aiAnalysisService"))
        assertTrue(registry.containsBeanDefinition("aiConfigService"))
    }

    @Test
    fun `the wiring marker is present on manually wired services only`() {
        assertTrue(AiAnalysisService::class.java.isAnnotationPresent(ManuallyWired::class.java))
        assertFalse(AiConfigService::class.java.isAnnotationPresent(ManuallyWired::class.java))
    }
}
