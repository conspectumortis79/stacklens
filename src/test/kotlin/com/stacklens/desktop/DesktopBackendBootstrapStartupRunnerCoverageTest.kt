package com.stacklens.desktop

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Configuration

class DesktopBackendBootstrapStartupRunnerCoverageTest {
    @Configuration(proxyBeanMethods = false)
    class StandaloneProbeApplication

    @Test
    fun `the default runner starts the given application with the standalone profile`() {
        val headlessBefore = System.getProperty("java.awt.headless")
        val context: ConfigurableApplicationContext =
            try {
                DefaultStartupRunner.run(
                    StandaloneProbeApplication::class.java,
                    arrayOf(
                        "--spring.main.web-application-type=none",
                        "--spring.main.banner-mode=off",
                    ),
                )
            } finally {
                if (headlessBefore == null) {
                    System.clearProperty("java.awt.headless")
                } else {
                    System.setProperty("java.awt.headless", headlessBefore)
                }
            }

        try {
            assertNotNull(context.getBean(StandaloneProbeApplication::class.java))
            assertTrue(context.environment.activeProfiles.contains("standalone"))
        } finally {
            context.close()
        }
    }
}
