package com.stacklens.adapter.out.textfilter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.core.io.DefaultResourceLoader

class TomlFilterAdapterTest {
    private fun newAdapter(enabled: Boolean = true): TomlFilterAdapter = TomlFilterAdapter(DefaultResourceLoader(), config = null, enabledProp = enabled)

    @Test fun `filter returns raw input when no spec matches`() {
        val adapter = newAdapter()
        val result =
            adapter.filter(
                command = listOf("totally-unknown-tool", "--flag"),
                stdout = "raw output\nwith two lines",
            )
        assertEquals("raw output\nwith two lines", result.text)
        assertEquals("<none>", result.specName)
    }

    @Test fun `filter returns raw input when disabled`() {
        val adapter = newAdapter(enabled = false)
        val result =
            adapter.filter(
                command = listOf("gradle", "build"),
                stdout = "> Task :app:test\nBUILD SUCCESSFUL",
            )
        assertEquals("> Task :app:test\nBUILD SUCCESSFUL", result.text)
        assertEquals("<disabled>", result.specName)
    }

    @Test fun `filter applies gradle spec to gradle build command`() {
        val adapter = newAdapter()
        val result =
            adapter.filter(
                command = listOf("./gradlew", "build"),
                stdout =
                    """
                    > Configuring project :app
                    > Task :app:compileJava UP-TO-DATE
                    > Task :app:compileKotlin UP-TO-DATE
                    > Task :app:test

                    3 tests completed, 1 failed

                    BUILD FAILED in 12s
                    """.trimIndent(),
            )
        assertTrue(result.text.contains("BUILD FAILED"))
        assertTrue(!result.text.contains("UP-TO-DATE"), "Should have stripped UP-TO-DATE")
    }

    @Test fun `filter applies phpunit spec to phpunit command`() {
        val adapter = newAdapter()
        val result =
            adapter.filter(
                command = listOf("vendor", "bin", "phpunit"),
                stdout =
                    """
                    PHPUnit 11.0.0 by Sebastian Bergmann and contributors.

                    ..F..                                                             5 / 5 (100%)

                    Time: 00:00.012, Memory: 8.00 MB

                    There was 1 failure:

                    1) App\Tests\FooTest::testBar
                    Failed asserting that false is true.

                    FAILURES!
                    Tests: 5, Assertions: 6, Failures: 1.
                    """.trimIndent(),
            )
        assertTrue(result.text.contains("1 failed") || result.text.contains("Failures: 1"))
    }

    @Test fun `filterByName throws for unknown spec`() {
        val adapter = newAdapter()
        val ex =
            assertThrows<IllegalArgumentException> {
                adapter.filterByName("does-not-exist", "anything")
            }
        assertTrue(ex.message!!.contains("does-not-exist"))
        assertTrue(ex.message!!.contains("Known specs"))
    }

    @Test fun `filterByName applies the named spec unconditionally`() {
        val adapter = newAdapter()
        val result = adapter.filterByName("du", output = "4.0K\t./src\n\n8.0K\t./tests\n16K\t.")
        assertTrue(!result.text.contains("\n\n"), "Should have collapsed blank lines")
        assertTrue(result.text.contains("./src"))
    }

    @Test fun `findBestSpec returns null for empty command`() {
        val adapter = newAdapter()
        assertEquals(null, adapter.findBestSpec(""))
    }

    @Test fun `findBestSpec returns null for totally unknown command`() {
        val adapter = newAdapter()
        assertEquals(null, adapter.findBestSpec("never-heard-of-this"))
    }

    @Test fun `findBestSpec prefers more specific regex over generic`() {
        val adapter = newAdapter()
        val spec = adapter.findBestSpec("composer install --no-dev")
        assertNotNull(spec)
        assertEquals("composer-install", spec!!.name)
    }

    @Test fun `knownFilters returns all loaded specs sorted`() {
        val adapter = newAdapter()
        val filters = adapter.knownFilters()
        assertEquals(filters.sorted(), filters)
        assertTrue(filters.size >= 50)
    }

    @Test fun `filter for liquibase uses stderr when filter_stderr is true`() {
        val adapter = newAdapter()
        val result =
            adapter.filter(
                command = listOf("liquibase", "status"),
                stdout = "",
                stderr = "Starting Liquibase at 10:00:00 (version 4.30.0)\nLiquibase command 'status' was executed successfully.\n",
            )
        assertTrue(result.text.contains("status"))
    }
}
