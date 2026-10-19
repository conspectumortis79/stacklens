package com.stacklens.desktop

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext

class RealSpringApplicationBuilderFactoryTest {
    @Test
    fun `build wraps a real SpringApplicationBuilder into a RealSpringApplicationRunner`() {
        val ctx = mock<ConfigurableApplicationContext>()
        val builder = mock<SpringApplicationBuilder>()
        whenever(builder.run(*arrayOf("--server.port=0"))).thenReturn(ctx)
        val runner = RealSpringApplicationBuilderFactory.RealSpringApplicationRunner(builder, arrayOf("--server.port=0"))
        assertNotNull(runner)
        val out = runner.run()
        assertSame(ctx, out)
        verify(builder).run("--server.port=0")
    }

    @Test
    fun `headless forwards the value and returns the same runner for chaining`() {
        val builder = mock<SpringApplicationBuilder>()
        val runner = RealSpringApplicationBuilderFactory.RealSpringApplicationRunner(builder, arrayOf())
        val same = runner.headless(true)
        assertSame(runner, same)
        verify(builder).headless(true)
    }

    @Test
    fun `profiles forwards the values and returns the same runner for chaining`() {
        val builder = mock<SpringApplicationBuilder>()
        val runner = RealSpringApplicationBuilderFactory.RealSpringApplicationRunner(builder, arrayOf())
        val same = runner.profiles("dev", "test")
        assertSame(runner, same)
        verify(builder).profiles("dev", "test")
    }

    @Test
    fun `web forwards the type and returns the same runner for chaining`() {
        val builder = mock<SpringApplicationBuilder>()
        val runner = RealSpringApplicationBuilderFactory.RealSpringApplicationRunner(builder, arrayOf())
        val same = runner.web(WebApplicationType.SERVLET)
        assertSame(runner, same)
        verify(builder).web(WebApplicationType.SERVLET)
    }

    @Test
    fun `registerShutdownHook forwards the value and returns the same runner for chaining`() {
        val builder = mock<SpringApplicationBuilder>()
        val runner = RealSpringApplicationBuilderFactory.RealSpringApplicationRunner(builder, arrayOf())
        val same = runner.registerShutdownHook(true)
        assertSame(runner, same)
        verify(builder).registerShutdownHook(true)
    }

    @Test
    fun `factory build returns a non-null SpringApplicationRunner`() {
        val factory = RealSpringApplicationBuilderFactory()
        val runner = factory.build(RealSpringApplicationBuilderFactoryTest::class.java, arrayOf("--server.port=0"))
        assertNotNull(runner)
    }
}
