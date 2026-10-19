package com.stacklens.infrastructure.config

import com.stacklens.adapter.out.demo.DemoRuntimeMode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DemoRuntimeModeTest {
    private var previous: String? = null

    @AfterEach
    fun restoreProperty() {
        val saved = previous
        if (saved == null) {
            System.clearProperty(DemoRuntimeMode.DOCKER_FLAG_PROPERTY)
        } else {
            System.setProperty(DemoRuntimeMode.DOCKER_FLAG_PROPERTY, saved)
        }
    }

    @Test
    fun `the ambient lookup reads the environment and the renamed system property`() {
        previous = System.getProperty(DemoRuntimeMode.DOCKER_FLAG_PROPERTY)

        assertEquals(System.getenv(DemoRuntimeMode.CONTAINER_ENV_VAR), DemoRuntimeMode.envValue())

        System.setProperty(DemoRuntimeMode.DOCKER_FLAG_PROPERTY, "true")
        assertTrue(DemoRuntimeMode.dockerFlag())

        System.setProperty(DemoRuntimeMode.DOCKER_FLAG_PROPERTY, "false")
        assertFalse(DemoRuntimeMode.dockerFlag())
    }

    @Test
    fun `insideContainer combines both ambient sources`() {
        previous = System.getProperty(DemoRuntimeMode.DOCKER_FLAG_PROPERTY)
        System.setProperty(DemoRuntimeMode.DOCKER_FLAG_PROPERTY, "true")

        assertTrue(DemoRuntimeMode.insideContainer())
        assertTrue(DemoRuntimeMode.insideContainer(envValue = "true", dockerFlag = false))
        assertTrue(DemoRuntimeMode.insideContainer(envValue = null, dockerFlag = true))
        assertFalse(DemoRuntimeMode.insideContainer(envValue = "false", dockerFlag = false))
    }
}
