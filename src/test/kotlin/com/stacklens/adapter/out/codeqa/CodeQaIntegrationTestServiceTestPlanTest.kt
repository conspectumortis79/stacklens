package com.stacklens.adapter.out.codeqa

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CodeQaIntegrationTestServiceTestPlanTest {
    @Test
    fun `TestPlan default constructor covers all defaults`() {
        val p = CodeQaIntegrationTestService.TestPlan()
        assertTrue(p.affectedServices.isEmpty())
        assertTrue(p.testCommands.isEmpty())
        assertEquals(20, p.waitSeconds)
        assertEquals("", p.composeDir)
    }

    @Test
    fun `TestPlan with values`() {
        val p =
            CodeQaIntegrationTestService.TestPlan(
                affectedServices = listOf("api"),
                testCommands = listOf("make test"),
                waitSeconds = 30,
                composeDir = "/tmp/compose",
            )
        assertEquals(listOf("api"), p.affectedServices)
        assertEquals(listOf("make test"), p.testCommands)
        assertEquals(30, p.waitSeconds)
        assertEquals("/tmp/compose", p.composeDir)
    }

    @Test
    fun `TestPlan exposes extended fields for project-aware test execution`() {
        val p =
            CodeQaIntegrationTestService.TestPlan(
                affectedServices = listOf("api"),
                testCommands = listOf("docker-compose exec api npx jest src/foo.test.ts"),
                projectTypes = listOf("node"),
                testStrategy = "unit-tests",
                testTargets = listOf("src/foo.test.ts", "src/foo.ts"),
                reasoning = "Runs jest targeted at the changed module.",
            )
        assertEquals(listOf("node"), p.projectTypes)
        assertEquals("unit-tests", p.testStrategy)
        assertEquals(listOf("src/foo.test.ts", "src/foo.ts"), p.testTargets)
        assertTrue(p.reasoning.startsWith("Runs jest"))
        assertEquals(listOf("api"), p.affectedServices)
        assertEquals(1, p.testCommands.size)
    }

    @Test
    fun `TestPlan defaults for new fields`() {
        val p = CodeQaIntegrationTestService.TestPlan(affectedServices = listOf("db"))
        assertTrue(p.projectTypes.isEmpty())
        assertEquals("", p.testStrategy)
        assertTrue(p.testTargets.isEmpty())
        assertEquals("", p.reasoning)
    }
}
