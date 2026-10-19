package com.stacklens.domain.text.filter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FilterBundleTest {
    @Test
    fun `spec returns matching FilterSpec by name`() {
        val spec1 =
            FilterSpec(
                name = "alpha",
                description = "a",
            )
        val spec2 =
            FilterSpec(
                name = "beta",
                description = "b",
            )
        val bundle =
            FilterBundle(
                specs = listOf(spec1, spec2),
                tests = emptyMap(),
            )

        val found = bundle.spec("alpha")
        assertNotNull(found)
        assertEquals("alpha", found!!.name)
        assertEquals("a", found.description)
    }

    @Test
    fun `spec returns null when no spec matches`() {
        val bundle =
            FilterBundle(
                specs =
                    listOf(
                        FilterSpec(name = "x", description = "x"),
                    ),
                tests = emptyMap(),
            )

        assertNull(bundle.spec("does-not-exist"))
    }

    @Test
    fun `constructor accepts tests whose keys match spec names`() {
        val spec =
            FilterSpec(
                name = "known",
                description = "k",
            )
        val testCases =
            listOf(
                FilterTestCase(name = "case-1", input = "in", expected = "out"),
            )
        val bundle =
            FilterBundle(
                specs = listOf(spec),
                tests = mapOf("known" to testCases),
            )

        assertEquals(1, bundle.tests.size)
        assertEquals(testCases, bundle.tests["known"])
        assertEquals(testCases, bundle.spec("known")?.let { bundle.tests[it.name] })
    }

    @Test
    fun `constructor throws when a tests key references an unknown spec`() {
        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                FilterBundle(
                    specs =
                        listOf(
                            FilterSpec(name = "known", description = "k"),
                        ),
                    tests = mapOf("ghost" to listOf(FilterTestCase("n", "i", "e"))),
                )
            }
        assertTrue(
            ex.message!!.contains("ghost"),
            "Expected error message to mention unknown spec, got: ${ex.message}",
        )
    }

    @Test
    fun `constructor accepts empty specs and empty tests`() {
        val bundle = FilterBundle(specs = emptyList(), tests = emptyMap())

        assertTrue(bundle.specs.isEmpty())
        assertTrue(bundle.tests.isEmpty())
        assertNull(bundle.spec("anything"))
    }

    @Test
    fun `constructor allows multiple tests for the same spec`() {
        val spec = FilterSpec(name = "s", description = "s")
        val cases =
            listOf(
                FilterTestCase(name = "a", input = "1", expected = "1"),
                FilterTestCase(name = "b", input = "22", expected = "22"),
            )
        val bundle =
            FilterBundle(
                specs = listOf(spec),
                tests = mapOf("s" to cases),
            )

        assertEquals(2, bundle.tests["s"]!!.size)
        assertEquals(cases, bundle.tests["s"])
    }
}
