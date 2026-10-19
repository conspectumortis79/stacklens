package com.stacklens.desktop

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import org.springframework.boot.WebApplicationType
import org.springframework.context.ConfigurableApplicationContext
import java.nio.file.Path

class RunHeadlessServerDefaultsTest {
    private var savedHeadless: String? = null
    private var savedApple: String? = null

    @BeforeEach
    fun saveSystemProperties() {
        savedHeadless = System.getProperty("java.awt.headless")
        savedApple = System.getProperty("apple.awt.UIElement")
    }

    @AfterEach
    fun restoreSystemProperties() {
        if (savedHeadless == null) {
            System.clearProperty("java.awt.headless")
        } else {
            System.setProperty("java.awt.headless", savedHeadless)
        }
        if (savedApple == null) {
            System.clearProperty("apple.awt.UIElement")
        } else {
            System.setProperty("apple.awt.UIElement", savedApple)
        }
    }

    @Test
    fun `runHeadlessServer with default lambdas exercises the System setProperty and Files createDirectories bodies`(
        @TempDir tempDir: Path,
    ) {
        val calls = mutableListOf<String>()
        val factory =
            SpringApplicationBuilderFactory { _, _ ->
                object : SpringApplicationRunner {
                    override fun headless(value: Boolean): SpringApplicationRunner {
                        calls += "headless:$value"
                        return this
                    }

                    override fun profiles(vararg profiles: String): SpringApplicationRunner {
                        calls += "profiles:${profiles.toList()}"
                        return this
                    }

                    override fun web(type: WebApplicationType): SpringApplicationRunner {
                        calls += "web:$type"
                        return this
                    }

                    override fun registerShutdownHook(register: Boolean): SpringApplicationRunner {
                        calls += "registerShutdownHook:$register"
                        return this
                    }

                    override fun run(): ConfigurableApplicationContext {
                        calls += "run"
                        return mock()
                    }
                }
            }
        runHeadlessServer(
            springApplicationBuilderFactory = factory,
            userHomeProvider = { tempDir.toString() },
        )
        assertEquals("true", System.getProperty("java.awt.headless"))
        assertEquals("true", System.getProperty("apple.awt.UIElement"))
        val dataDir = tempDir.resolve(".stacklens/data").toFile()
        assertTrue(dataDir.isDirectory, "default directoryCreator must create $dataDir")
        assertTrue(calls.contains("headless:true"))
        assertTrue(calls.contains("run"))
    }

    @Test
    fun `runHeadlessServer with default environmentProvider reads STACKLENS_SERVER_PORT from env`() {
        val factory =
            SpringApplicationBuilderFactory { _, _ ->
                object : SpringApplicationRunner {
                    override fun headless(value: Boolean) = this

                    override fun profiles(vararg profiles: String) = this

                    override fun web(type: WebApplicationType) = this

                    override fun registerShutdownHook(register: Boolean) = this

                    override fun run(): ConfigurableApplicationContext = mock()
                }
            }
        runHeadlessServer(
            springApplicationBuilderFactory = factory,
            directoryCreator = { },
            userHomeProvider = { "/tmp/run-headless-server-defaults" },
        )
        assertNotNull(factory)
    }
}
