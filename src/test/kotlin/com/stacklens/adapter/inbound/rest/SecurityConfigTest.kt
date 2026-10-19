package com.stacklens.adapter.inbound.rest

import com.stacklens.infrastructure.config.SecurityConfig
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.security.web.SecurityFilterChain

class SecurityConfigTest {
    private val contextRunner =
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SecurityAutoConfiguration::class.java))
            .withUserConfiguration(SecurityConfig::class.java)

    @Test
    fun `securityFilterChain bean is created`() {
        contextRunner.run { context ->
            assertNotNull(context.getBean(SecurityFilterChain::class.java))
        }
    }
}
