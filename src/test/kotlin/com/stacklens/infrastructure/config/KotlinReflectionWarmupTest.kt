package com.stacklens.infrastructure.config

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.boot.DefaultApplicationArguments

class KotlinReflectionWarmupTest {
    private val args = DefaultApplicationArguments()

    @Test
    fun `run completes without throwing`() {
        val warmup = KotlinReflectionWarmup()
        warmup.run(args)
        assertNotNull(warmup)
    }

    @Test
    fun `warmup can be instantiated and reused`() {
        val warmup = KotlinReflectionWarmup()
        warmup.run(args)
        warmup.run(args)
        assertNotNull(warmup)
    }

    @Test
    fun `run swallows exceptions thrown by probe`() {
        val warmup =
            object : KotlinReflectionWarmup() {
                override fun probe(): Unit = throw IllegalStateException("simulated reflection failure")
            }
        warmup.run(args)
        assertNotNull(warmup)
    }
}
